package com.saasbase.auth.adapter;

import com.saasbase.auth.application.AuthApplicationService;
import com.saasbase.auth.application.RefreshRequest;
import com.saasbase.auth.application.dto.LoginRequest;
import com.saasbase.auth.application.dto.LoginResponse;
import com.saasbase.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {
    private AuthApplicationService authApplicationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authApplicationService = mock(AuthApplicationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_route_calls_application_service() throws Exception {
        when(authApplicationService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse("Bearer", "access-1", "refresh-1", 900));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"tenantCode\":\"tenant-a\",\"username\":\"alice\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-1"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-1"));

        verify(authApplicationService).login(any(LoginRequest.class));
    }

    @Test
    void refresh_route_calls_application_service() throws Exception {
        when(authApplicationService.refresh(any(RefreshRequest.class)))
                .thenReturn(new LoginResponse("Bearer", "access-2", "refresh-2", 900));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-2"));

        verify(authApplicationService).refresh(any(RefreshRequest.class));
    }
}
