package com.saasbase.iam.adapter;

import com.saasbase.common.api.PageResponse;
import com.saasbase.common.error.GlobalExceptionHandler;
import com.saasbase.common.tenant.TenantContext;
import com.saasbase.common.tenant.TenantContextHolder;
import com.saasbase.iam.application.UserApplicationService;
import com.saasbase.iam.application.dto.UserCommands.ChangePasswordCommand;
import com.saasbase.iam.application.dto.UserCommands.CreateUserCommand;
import com.saasbase.iam.application.dto.UserCommands.UpdateUserCommand;
import com.saasbase.iam.application.dto.UserView;
import com.saasbase.iam.domain.UserPageQuery;
import com.saasbase.iam.domain.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerTest {
    private final UserApplicationService service = mock(UserApplicationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        TenantContextHolder.set(new TenantContext(2001L, 3002L, false));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("op", "N/A", Set.of(new SimpleGrantedAuthority("tenant:user:create"))));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void routesAndAuthoritiesAreDeclaredAsExpected() throws Exception {
        assertPreAuthorize("create", "tenant:user:create");
        assertPreAuthorize("page", "tenant:user:read");
        assertPreAuthorize("get", "tenant:user:read");
        assertPreAuthorize("update", "tenant:user:update");
        assertPreAuthorize("enable", "tenant:user:enable");
        assertPreAuthorize("disable", "tenant:user:disable");
        assertPreAuthorize("resetPassword", "tenant:user:reset-password");
    }

    @Test
    void createUsesTenantContextAndHidesSensitiveFields() throws Exception {
        when(service.create(anyLong(), anyLong(), any())).thenReturn(sampleView());

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("op")
                                .authorities(new SimpleGrantedAuthority("tenant:user:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","initialPassword":"secret","displayName":"Alice","roleIds":[1]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.initialPassword").doesNotExist());

        verify(service).create(org.mockito.ArgumentMatchers.eq(2001L), org.mockito.ArgumentMatchers.eq(3002L), any(CreateUserCommand.class));
    }

    @Test
    void pageValidatesParametersAndUsesTenantContext() throws Exception {
        when(service.page(anyLong(), any(UserPageQuery.class))).thenReturn(new PageResponse<>(java.util.List.of(sampleView()), 1, 1, 20));

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("op")
                                .authorities(new SimpleGrantedAuthority("tenant:user:read"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("op")
                                .authorities(new SimpleGrantedAuthority("tenant:user:read")))
                        .param("page", "0"))
                .andExpect(status().isBadRequest());

        ArgumentCaptor<UserPageQuery> captor = ArgumentCaptor.forClass(UserPageQuery.class);
        verify(service).page(org.mockito.ArgumentMatchers.eq(2001L), captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(1);
        assertThat(captor.getValue().size()).isEqualTo(20);
        assertThat(captor.getValue().status()).isNull();
    }

    @Test
    void getAndUpdateAndToggleRoutesWorkWithoutSensitiveFields() throws Exception {
        when(service.get(anyLong(), anyLong())).thenReturn(sampleView());
        when(service.update(anyLong(), anyLong(), any(UpdateUserCommand.class))).thenReturn(sampleView());
        when(service.enable(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(sampleView());
        when(service.disable(anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(sampleView());
        when(service.resetPassword(anyLong(), anyLong(), anyLong(), any(ChangePasswordCommand.class))).thenReturn(sampleView());

        mockMvc.perform(get("/api/v1/admin/users/1")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("op")
                                .authorities(new SimpleGrantedAuthority("tenant:user:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        mockMvc.perform(put("/api/v1/admin/users/1")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("op")
                                .authorities(new SimpleGrantedAuthority("tenant:user:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"displayName":"Alice","roleIds":[1],"version":1}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/users/1/enable")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("op")
                                .authorities(new SimpleGrantedAuthority("tenant:user:enable")))
                        .param("version", "1"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/users/1/disable")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("op")
                                .authorities(new SimpleGrantedAuthority("tenant:user:disable")))
                        .param("version", "1"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/users/1/reset-password")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("op")
                                .authorities(new SimpleGrantedAuthority("tenant:user:reset-password")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"newPassword":"secret","version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.initialPassword").doesNotExist());
    }

    private void assertPreAuthorize(String methodName, String expectedAuthority) throws Exception {
        Method method = AdminUserController.class.getDeclaredMethod(methodName,
                methodName.equals("create") ? new Class[]{CreateUserCommand.class} :
                        methodName.equals("page") ? new Class[]{int.class, int.class, String.class, Long.class, UserStatus.class, String.class} :
                                methodName.equals("get") ? new Class[]{long.class} :
                                        methodName.equals("update") ? new Class[]{long.class, UpdateUserCommand.class} :
                                                methodName.equals("enable") || methodName.equals("disable") ? new Class[]{long.class, long.class} :
                                                        new Class[]{long.class, ChangePasswordCommand.class});
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('" + expectedAuthority + "')");
    }

    private UserView sampleView() {
        return new UserView(1L, "alice", "Alice", null, null, UserStatus.ACTIVE, 1L, false, Set.of(1L));
    }
}
