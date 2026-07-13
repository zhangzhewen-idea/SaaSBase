package com.saasbase.tenant.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbase.common.api.PageResponse;
import com.saasbase.tenant.application.TenantApplicationService;
import com.saasbase.tenant.application.dto.CreateTenantRequest;
import com.saasbase.tenant.application.dto.TenantQuery;
import com.saasbase.tenant.application.dto.TenantResponse;
import com.saasbase.tenant.application.dto.UpdateTenantRequest;
import com.saasbase.tenant.domain.TenantStatus;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformTenantControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void controller_methods_expose_expected_permissions() throws NoSuchMethodException {
        assertThat(method("create", CreateTenantRequest.class, Long.class).getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('platform:tenant:create')");
        assertThat(method("page", TenantQuery.class).getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('platform:tenant:read')");
        assertThat(method("get", Long.class).getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('platform:tenant:read')");
        assertThat(method("update", Long.class, UpdateTenantRequest.class, Long.class).getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('platform:tenant:update')");
        assertThat(method("enable", Long.class, Long.class).getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('platform:tenant:enable')");
        assertThat(method("disable", Long.class, Long.class).getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('platform:tenant:disable')");
    }

    @Test
    void create_endpoint_serializes_response() throws Exception {
        TenantApplicationService service = mock(TenantApplicationService.class);
        when(service.create(any(CreateTenantRequest.class), eq(9L)))
                .thenReturn(new TenantResponse(1L, "acme", "Acme", TenantStatus.ACTIVE, 0L, 0L));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PlatformTenantController(service)).build();

        mockMvc.perform(post("/api/v1/platform/tenants")
                        .param("operatorId", "9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest(
                                "acme", "Acme", "admin", "管理员", "Secret123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantCode").value("acme"))
                .andExpect(jsonPath("$.data.sessionVersion").value(0));
    }

    @Test
    void page_get_update_enable_and_disable_endpoints_work() throws Exception {
        TenantApplicationService service = mock(TenantApplicationService.class);
        when(service.page(new TenantQuery(null, null, null, 1, 20)))
                .thenReturn(new PageResponse<>(List.of(
                        new TenantResponse(1L, "acme", "Acme", TenantStatus.ACTIVE, 0L, 0L)), 1, 1, 20));
        when(service.get(1L)).thenReturn(new TenantResponse(1L, "acme", "Acme", TenantStatus.ACTIVE, 0L, 0L));
        when(service.update(eq(1L), any(UpdateTenantRequest.class), eq(9L)))
                .thenReturn(new TenantResponse(1L, "acme", "After", TenantStatus.ACTIVE, 0L, 0L));
        when(service.enable(1L, 9L))
                .thenReturn(new TenantResponse(1L, "acme", "After", TenantStatus.ACTIVE, 0L, 0L));
        when(service.disable(1L, 9L))
                .thenReturn(new TenantResponse(1L, "acme", "After", TenantStatus.DISABLED, 1L, 0L));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PlatformTenantController(service)).build();

        mockMvc.perform(get("/api/v1/platform/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].tenantCode").value("acme"));
        mockMvc.perform(get("/api/v1/platform/tenants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantCode").value("acme"));
        mockMvc.perform(put("/api/v1/platform/tenants/1")
                        .param("operatorId", "9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTenantRequest("After"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantName").value("After"));
        mockMvc.perform(post("/api/v1/platform/tenants/1/enable").param("operatorId", "9"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/platform/tenants/1/disable").param("operatorId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return PlatformTenantController.class.getDeclaredMethod(name, parameterTypes);
    }
}
