package com.saasbase.iam.domain;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;

public final class LastTenantAdminPolicy {

    public void ensureAssignable(boolean targetHasTenantAdmin, boolean removingTenantAdmin, long activeTenantAdminCount) {
        if (targetHasTenantAdmin && removingTenantAdmin && activeTenantAdminCount <= 1) {
            throw new BizException(ErrorCode.IAM_LAST_TENANT_ADMIN);
        }
    }
}
