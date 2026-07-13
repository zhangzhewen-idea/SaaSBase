package com.saasbase.iam.application;

import com.saasbase.audit.domain.AdminOperationAuditEvent;
import com.saasbase.audit.domain.gateway.AuditGateway;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.iam.application.dto.DepartmentCommands.CreateDepartmentCommand;
import com.saasbase.iam.application.dto.DepartmentCommands.MoveDepartmentCommand;
import com.saasbase.iam.application.dto.DepartmentCommands.ToggleDepartmentCommand;
import com.saasbase.iam.application.dto.DepartmentCommands.TransferDepartmentCommand;
import com.saasbase.iam.application.dto.DepartmentCommands.UpdateDepartmentCommand;
import com.saasbase.iam.application.dto.DepartmentViews.DepartmentTreeView;
import com.saasbase.iam.domain.Department;
import com.saasbase.iam.domain.DepartmentStatus;
import com.saasbase.iam.domain.DepartmentTreeNode;
import com.saasbase.iam.domain.IamUser;
import com.saasbase.iam.domain.gateway.DepartmentGateway;
import com.saasbase.iam.domain.gateway.DepartmentMemberGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DepartmentApplicationService {
    private final DepartmentGateway departmentGateway;
    private final DepartmentMemberGateway departmentMemberGateway;
    private final AuditGateway auditGateway;

    public DepartmentApplicationService(DepartmentGateway departmentGateway,
                                        DepartmentMemberGateway departmentMemberGateway,
                                        AuditGateway auditGateway) {
        this.departmentGateway = departmentGateway;
        this.departmentMemberGateway = departmentMemberGateway;
        this.auditGateway = auditGateway;
    }

    @Transactional(readOnly = true)
    public List<DepartmentTreeView> tree(long tenantId) {
        List<Department> departments = departmentGateway.listByTenant(tenantId);
        DepartmentTreeNode root = buildTree(departments);
        return root == null ? List.of() : List.of(toView(root));
    }

    @Transactional(readOnly = true)
    public DepartmentTreeView get(long tenantId, long deptId) {
        Department department = requireDepartment(tenantId, deptId);
        return toView(new DepartmentTreeNode(department.id(), department.parentId(), department.deptCode(), department.deptName(),
                department.sortOrder(), department.status(), List.of()));
    }

    @Transactional
    public DepartmentTreeView create(long tenantId, long operatorId, CreateDepartmentCommand command) {
        departmentGateway.findById(tenantId, command.parentId())
                .filter(parent -> parent.status() == DepartmentStatus.ACTIVE)
                .orElseThrow(() -> new BizException(ErrorCode.IAM_DEPARTMENT_NOT_FOUND));
        if (departmentGateway.existsByCode(tenantId, command.deptCode())) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_CODE_CONFLICT);
        }
        long depth = departmentGateway.depthOf(tenantId, command.parentId()) + 1;
        if (depth > 10) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_DEPTH_LIMIT_EXCEEDED);
        }
        Department department = new Department(nextId(), tenantId, command.parentId(), command.deptCode(), command.deptName(),
                command.sortOrder(), DepartmentStatus.ACTIVE, 0L);
        departmentGateway.insert(department, operatorId);
        audit(tenantId, operatorId, "CREATE", department.id());
        return toView(new DepartmentTreeNode(department.id(), department.parentId(), department.deptCode(), department.deptName(),
                department.sortOrder(), department.status(), List.of()));
    }

    @Transactional
    public DepartmentTreeView update(long tenantId, long operatorId, long deptId, UpdateDepartmentCommand command) {
        Department department = requireDepartment(tenantId, deptId);
        assertActiveOrDisabled(department);
        DepartmentStatus status = department.status();
        Department updated = new Department(department.id(), department.tenantId(), department.parentId(), department.deptCode(),
                command.deptName(), command.sortOrder(), status, command.version());
        if (!departmentGateway.update(updated, operatorId)) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_CONCURRENT_MODIFICATION);
        }
        audit(tenantId, operatorId, "UPDATE", deptId);
        return toView(new DepartmentTreeNode(updated.id(), updated.parentId(), updated.deptCode(), updated.deptName(),
                updated.sortOrder(), updated.status(), List.of()));
    }

    @Transactional
    public DepartmentTreeView move(long tenantId, long operatorId, long deptId, MoveDepartmentCommand command) {
        Department department = requireDepartment(tenantId, deptId);
        if (deptId == rootDeptId(tenantId)) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_ROOT_NOT_ALLOWED);
        }
        if (deptId == command.newParentId()) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_CYCLE_NOT_ALLOWED);
        }
        Department parent = requireDepartment(tenantId, command.newParentId());
        if (parent.status() != DepartmentStatus.ACTIVE) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_DISABLED);
        }
        if (departmentGateway.isDescendant(tenantId, deptId, command.newParentId())) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_CYCLE_NOT_ALLOWED);
        }
        long depth = departmentGateway.depthOf(tenantId, command.newParentId()) + departmentGateway.subtreeDepth(tenantId, deptId);
        if (depth > 10) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_DEPTH_LIMIT_EXCEEDED);
        }
        Department moved = new Department(department.id(), department.tenantId(), command.newParentId(), department.deptCode(),
                department.deptName(), department.sortOrder(), department.status(), command.version());
        moved.moveTo(command.newParentId());
        if (!departmentGateway.update(moved, operatorId)) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_CONCURRENT_MODIFICATION);
        }
        audit(tenantId, operatorId, "MOVE", deptId);
        return toView(new DepartmentTreeNode(moved.id(), moved.parentId(), moved.deptCode(), moved.deptName(),
                moved.sortOrder(), moved.status(), List.of()));
    }

    @Transactional
    public DepartmentTreeView disable(long tenantId, long operatorId, long deptId, ToggleDepartmentCommand command) {
        Department department = requireDepartment(tenantId, deptId);
        if (deptId == rootDeptId(tenantId)) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_ROOT_NOT_ALLOWED);
        }
        Department updated = new Department(department.id(), department.tenantId(), department.parentId(), department.deptCode(),
                department.deptName(), department.sortOrder(), department.status(), command.version());
        updated.disable();
        if (!departmentGateway.update(updated, operatorId)) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_CONCURRENT_MODIFICATION);
        }
        audit(tenantId, operatorId, "DISABLE", deptId);
        return toView(new DepartmentTreeNode(updated.id(), updated.parentId(), updated.deptCode(), updated.deptName(),
                updated.sortOrder(), updated.status(), List.of()));
    }

    @Transactional
    public DepartmentTreeView enable(long tenantId, long operatorId, long deptId, ToggleDepartmentCommand command) {
        Department department = requireDepartment(tenantId, deptId);
        Department updated = new Department(department.id(), department.tenantId(), department.parentId(), department.deptCode(),
                department.deptName(), department.sortOrder(), department.status(), command.version());
        updated.enable();
        if (!departmentGateway.update(updated, operatorId)) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_CONCURRENT_MODIFICATION);
        }
        audit(tenantId, operatorId, "ENABLE", deptId);
        return toView(new DepartmentTreeNode(updated.id(), updated.parentId(), updated.deptCode(), updated.deptName(),
                updated.sortOrder(), updated.status(), List.of()));
    }

    @Transactional
    public void delete(long tenantId, long operatorId, long deptId, ToggleDepartmentCommand command) {
        Department department = requireDepartment(tenantId, deptId);
        if (deptId == rootDeptId(tenantId)) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_ROOT_NOT_ALLOWED);
        }
        if (departmentGateway.countChildren(tenantId, deptId) > 0 || departmentMemberGateway.countDirectMembers(tenantId, deptId) > 0) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_NOT_EMPTY);
        }
        if (!departmentGateway.delete(tenantId, deptId, command.version(), operatorId)) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_CONCURRENT_MODIFICATION);
        }
        audit(tenantId, operatorId, "DELETE", deptId);
    }

    @Transactional(readOnly = true)
    public List<com.saasbase.iam.application.dto.UserView> members(long tenantId, long deptId, boolean descendants) {
        requireDepartment(tenantId, deptId);
        List<IamUser> users = descendants
                ? departmentMemberGateway.listDescendantMembers(tenantId, deptId)
                : departmentMemberGateway.listDirectMembers(tenantId, deptId);
        return users.stream()
                .map(user -> new com.saasbase.iam.application.dto.UserView(user.id(), user.username(), user.username(), null,
                        user.primaryDepartmentId(), user.status(), user.sessionVersion(), user.mustChangePassword(), java.util.Set.of()))
                .toList();
    }

    @Transactional
    public void transferDepartment(long tenantId, long operatorId, long userId, TransferDepartmentCommand command) {
        departmentMemberGateway.assertDepartmentActive(tenantId, command.departmentId());
        if (!departmentMemberGateway.existsUser(tenantId, userId)) {
            throw new BizException(ErrorCode.IAM_USER_NOT_FOUND);
        }
        departmentMemberGateway.transferDepartment(tenantId, userId, command.departmentId(), command.version());
        audit(tenantId, operatorId, "TRANSFER_DEPT", userId);
    }

    private Department requireDepartment(long tenantId, long deptId) {
        return departmentGateway.findById(tenantId, deptId)
                .orElseThrow(() -> new BizException(ErrorCode.IAM_DEPARTMENT_NOT_FOUND));
    }

    private long rootDeptId(long tenantId) {
        return departmentGateway.listByTenant(tenantId).stream()
                .filter(dept -> Department.ROOT_CODE.equals(dept.deptCode()))
                .mapToLong(Department::id)
                .findFirst()
                .orElse(-1L);
    }

    private void assertActiveOrDisabled(Department department) {
        if (department == null) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_NOT_FOUND);
        }
    }

    private void audit(long tenantId, long operatorId, String operation, long resourceId) {
        auditGateway.appendAdminOperationAudit(new AdminOperationAuditEvent(
                tenantId, operatorId, operation, "IAM_DEPT", String.valueOf(resourceId), null, Instant.now()));
    }

    private DepartmentTreeNode buildTree(List<Department> departments) {
        Map<Long, List<Department>> children = new HashMap<>();
        Department root = null;
        for (Department department : departments) {
            if (department.parentId() == null) {
                root = department;
            }
            children.computeIfAbsent(department.parentId(), key -> new ArrayList<>()).add(department);
        }
        if (root == null) {
            return null;
        }
        return toNode(root, children);
    }

    private DepartmentTreeNode toNode(Department department, Map<Long, List<Department>> children) {
        List<DepartmentTreeNode> childNodes = children.getOrDefault(department.id(), List.of()).stream()
                .sorted(Comparator.comparingLong(Department::sortOrder).thenComparing(Department::id))
                .map(child -> toNode(child, children))
                .toList();
        return new DepartmentTreeNode(department.id(), department.parentId(), department.deptCode(), department.deptName(),
                department.sortOrder(), department.status(), childNodes);
    }

    private DepartmentTreeView toView(DepartmentTreeNode node) {
        if (node == null) {
            return null;
        }
        List<DepartmentTreeView> children = node.children().stream().map(this::toView).toList();
        return new DepartmentTreeView(node.id(), node.parentId(), node.deptCode(), node.deptName(), node.sortOrder(), node.status(), children);
    }

    private long nextId() {
        return System.currentTimeMillis();
    }
}
