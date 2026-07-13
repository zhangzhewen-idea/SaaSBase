package com.saasbase.iam.domain.gateway;

import com.saasbase.common.api.PageResponse;
import com.saasbase.iam.domain.IamUser;
import com.saasbase.iam.domain.UserPageQuery;

import java.util.Optional;

public interface UserGateway {
    boolean existsByUsername(long tenantId, String username);

    Optional<IamUser> findById(long tenantId, long userId);

    PageResponse<IamUser> page(long tenantId, UserPageQuery query);

    void insert(IamUser user);

    boolean update(IamUser user);
}
