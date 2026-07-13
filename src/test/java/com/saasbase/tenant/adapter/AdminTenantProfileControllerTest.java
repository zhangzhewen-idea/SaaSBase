package com.saasbase.tenant.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saasbase.common.tenant.TenantContext;
import com.saasbase.common.tenant.TenantContextHolder;
import com.saasbase.tenant.application.TenantApplicationService;
import com.saasbase.tenant.application.dto.TenantResponse;
import com.saasbase.tenant.domain.TenantStatus;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminTenantProfileControllerTest {

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void controller_method_exposes_expected_permission() throws NoSuchMethodException {
        Method method = AdminTenantController.class.getDeclaredMethod("profile");
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('tenant:profile:read')");
    }

    @Test
    void profile_endpoint_uses_current_tenant_context() throws Exception {
        TenantContextHolder.set(new TenantContext(7L, 1L, false));
        TenantApplicationService service = mock(TenantApplicationService.class);
        when(service.currentProfile(7L))
                .thenReturn(new TenantResponse(7L, "acme", "Acme", TenantStatus.ACTIVE, 3L, 2L));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminTenantController(service)).build();

        mockMvc.perform(get("/api/v1/admin/tenant/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantCode").value("acme"))
                .andExpect(jsonPath("$.data.sessionVersion").value(3));
    }
}
