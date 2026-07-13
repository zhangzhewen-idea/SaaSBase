package com.saasbase.iam.infrastructure.persistence;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.iam.domain.Department;
import com.saasbase.iam.domain.DepartmentStatus;
import com.saasbase.iam.domain.IamUser;
import com.saasbase.iam.domain.gateway.DepartmentGateway;
import com.saasbase.iam.domain.gateway.DepartmentMemberGateway;
import com.saasbase.iam.domain.gateway.DepartmentReferenceGateway;
import com.saasbase.tenant.domain.gateway.TenantDepartmentInitializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public class DepartmentPersistenceAdapter implements DepartmentGateway, DepartmentMemberGateway, DepartmentReferenceGateway, TenantDepartmentInitializer {
    private final JdbcTemplate jdbcTemplate;

    public DepartmentPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsByCode(long tenantId, String deptCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM iam_dept WHERE tenant_id = ? AND dept_code = ? AND deleted = 0
                """, Integer.class, tenantId, deptCode);
        return count != null && count > 0;
    }

    @Override
    public Optional<Department> findById(long tenantId, long deptId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, parent_id, dept_code, dept_name, sort_order, status, version, deleted
                          FROM iam_dept
                         WHERE tenant_id = ?
                           AND id = ?
                           AND deleted = 0
                        """,
                this::mapDepartment, tenantId, deptId).stream().findFirst();
    }

    @Override
    public List<Department> listByTenant(long tenantId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, parent_id, dept_code, dept_name, sort_order, status, version, deleted
                          FROM iam_dept
                         WHERE tenant_id = ?
                           AND deleted = 0
                         ORDER BY COALESCE(parent_id, 0), sort_order, id
                        """,
                this::mapDepartment, tenantId);
    }

    @Override
    public void insert(Department department, long operatorId) {
        jdbcTemplate.update("""
                        INSERT INTO iam_dept (id, tenant_id, parent_id, dept_code, dept_name, sort_order, status,
                                              created_at, created_by, updated_at, updated_by, deleted, version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), ?, CURRENT_TIMESTAMP(6), ?, 0, ?)
                        """,
                department.id(), department.tenantId(), department.parentId(), department.deptCode(), department.deptName(),
                department.sortOrder(), department.status().name(), operatorId, operatorId, department.version());
    }

    @Override
    public boolean update(Department department, long operatorId) {
        return jdbcTemplate.update("""
                        UPDATE iam_dept
                           SET parent_id = ?,
                               dept_name = ?,
                               sort_order = ?,
                               status = ?,
                               updated_at = CURRENT_TIMESTAMP(6),
                               updated_by = ?,
                               version = version + 1
                         WHERE tenant_id = ?
                           AND id = ?
                           AND deleted = 0
                           AND version = ?
                        """,
                department.parentId(), department.deptName(), department.sortOrder(), department.status().name(), operatorId,
                department.tenantId(), department.id(), department.version()) == 1;
    }

    @Override
    public boolean delete(long tenantId, long deptId, long version, long operatorId) {
        return jdbcTemplate.update("""
                        UPDATE iam_dept
                           SET deleted = 1,
                               deleted_at = CURRENT_TIMESTAMP(6),
                               deleted_by = ?,
                               updated_at = CURRENT_TIMESTAMP(6),
                               updated_by = ?,
                               version = version + 1
                         WHERE tenant_id = ?
                           AND id = ?
                           AND deleted = 0
                           AND version = ?
                        """,
                operatorId, operatorId, tenantId, deptId, version) == 1;
    }

    @Override
    public long countChildren(long tenantId, long deptId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM iam_dept
                 WHERE tenant_id = ?
                   AND parent_id = ?
                   AND deleted = 0
                """, Long.class, tenantId, deptId);
        return count == null ? 0L : count;
    }

    @Override
    public boolean isDescendant(long tenantId, long ancestorDeptId, long descendantDeptId) {
        List<Long> ids = jdbcTemplate.queryForList("""
                WITH RECURSIVE dept_tree AS (
                    SELECT id, parent_id
                      FROM iam_dept
                     WHERE tenant_id = ?
                       AND id = ?
                       AND deleted = 0
                    UNION ALL
                    SELECT d.id, d.parent_id
                      FROM iam_dept d
                      JOIN dept_tree dt ON d.parent_id = dt.id
                     WHERE d.tenant_id = ?
                       AND d.deleted = 0
                )
                SELECT id FROM dept_tree
                """, Long.class, tenantId, ancestorDeptId, tenantId);
        return ids.contains(descendantDeptId);
    }

    @Override
    public long depthOf(long tenantId, Long deptId) {
        if (deptId == null) {
            return 0L;
        }
        List<Department> departments = listByTenant(tenantId);
        long depth = 1L;
        Long current = deptId;
        while (current != null) {
            Long currentId = current;
            Optional<Department> department = departments.stream().filter(item -> item.id().equals(currentId)).findFirst();
            if (department.isEmpty()) {
                break;
            }
            current = department.get().parentId();
            if (current != null) {
                depth++;
            }
        }
        return depth;
    }

    @Override
    public long subtreeDepth(long tenantId, long deptId) {
        List<Long> levels = jdbcTemplate.queryForList("""
                WITH RECURSIVE dept_tree AS (
                    SELECT id, 1 AS level
                      FROM iam_dept
                     WHERE tenant_id = ?
                       AND id = ?
                       AND deleted = 0
                    UNION ALL
                    SELECT d.id, dt.level + 1
                      FROM iam_dept d
                      JOIN dept_tree dt ON d.parent_id = dt.id
                     WHERE d.tenant_id = ?
                       AND d.deleted = 0
                )
                SELECT level FROM dept_tree
                """, Long.class, tenantId, deptId, tenantId);
        return levels.stream().mapToLong(Long::longValue).max().orElse(1L);
    }

    @Override
    public void assertDepartmentActive(long tenantId, long departmentId) {
        if (findById(tenantId, departmentId).filter(dept -> dept.status() == DepartmentStatus.ACTIVE).isEmpty()) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_DISABLED);
        }
    }

    @Override
    public List<IamUser> listDirectMembers(long tenantId, long departmentId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, username, password_hash, display_name, phone, primary_department_id, status,
                               must_change_password, session_version
                          FROM iam_user
                         WHERE tenant_id = ?
                           AND primary_department_id = ?
                           AND deleted = 0
                         ORDER BY created_at DESC, id DESC
                        """,
                this::mapUser, tenantId, departmentId);
    }

    @Override
    public List<IamUser> listDescendantMembers(long tenantId, long departmentId) {
        return jdbcTemplate.query("""
                        WITH RECURSIVE dept_tree AS (
                            SELECT id
                              FROM iam_dept
                             WHERE tenant_id = ?
                               AND id = ?
                               AND deleted = 0
                            UNION ALL
                            SELECT d.id
                              FROM iam_dept d
                              JOIN dept_tree dt ON d.parent_id = dt.id
                             WHERE d.tenant_id = ?
                               AND d.deleted = 0
                        )
                        SELECT u.id, u.tenant_id, u.username, u.password_hash, u.display_name, u.phone, u.primary_department_id,
                               u.status, u.must_change_password, u.session_version
                          FROM iam_user u
                          JOIN dept_tree dt ON u.primary_department_id = dt.id
                         WHERE u.tenant_id = ?
                           AND u.deleted = 0
                         ORDER BY u.created_at DESC, u.id DESC
                        """,
                this::mapUser, tenantId, departmentId, tenantId, tenantId);
    }

    @Override
    public void transferDepartment(long tenantId, long userId, long departmentId, long version) {
        int updated = jdbcTemplate.update("""
                        UPDATE iam_user
                           SET primary_department_id = ?,
                               version = version + 1,
                               updated_at = CURRENT_TIMESTAMP(6)
                         WHERE tenant_id = ?
                           AND id = ?
                           AND deleted = 0
                           AND version = ?
                        """, departmentId, tenantId, userId, version);
        if (updated != 1) {
            throw new BizException(ErrorCode.IAM_USER_CONCURRENT_MODIFICATION);
        }
    }

    @Override
    public long countDirectMembers(long tenantId, long departmentId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM iam_user WHERE tenant_id = ? AND primary_department_id = ? AND deleted = 0
                """, Long.class, tenantId, departmentId);
        return count == null ? 0L : count;
    }

    @Override
    public boolean existsUser(long tenantId, long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM iam_user WHERE tenant_id = ? AND id = ? AND deleted = 0
                """, Integer.class, tenantId, userId);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void initializeRootDepartment(Long tenantId, String departmentName, Long operatorId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM iam_dept WHERE tenant_id = ? AND dept_code = 'ROOT' AND deleted = 0
                """, Long.class, tenantId);
        if (count != null && count > 0) {
            return;
        }
        long id = System.currentTimeMillis();
        jdbcTemplate.update("""
                        INSERT INTO iam_dept (id, tenant_id, parent_id, dept_code, dept_name, sort_order, status,
                                              created_at, created_by, updated_at, updated_by, deleted, version)
                        VALUES (?, ?, NULL, 'ROOT', ?, 0, 'ACTIVE', CURRENT_TIMESTAMP(6), ?, CURRENT_TIMESTAMP(6), ?, 0, 0)
                        """,
                id, tenantId, departmentName, operatorId, operatorId);
    }

    private Department mapDepartment(ResultSet rs, int rowNum) throws SQLException {
        return new Department(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getObject("parent_id", Long.class),
                rs.getString("dept_code"),
                rs.getString("dept_name"),
                rs.getLong("sort_order"),
                DepartmentStatus.valueOf(rs.getString("status")),
                rs.getLong("version"));
    }

    private IamUser mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new IamUser(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getString("username"),
                rs.getObject("primary_department_id", Long.class),
                rs.getString("password_hash"),
                com.saasbase.iam.domain.UserStatus.valueOf(rs.getString("status")),
                rs.getBoolean("must_change_password"),
                rs.getLong("session_version"));
    }
}
