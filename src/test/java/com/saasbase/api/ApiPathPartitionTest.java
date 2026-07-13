package com.saasbase.api;

import com.saasbase.SaaSBaseApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SaaSBaseApplication.class)
@ActiveProfiles("staging")
class ApiPathPartitionTest {

    @Autowired
    WebApplicationContext context;

    @Test
    void open_api_reserved_path_is_not_implemented_in_phase_one() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mockMvc.perform(get("/api/v1/open/ping"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/platform/tenants").with(user("test")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/tenant/profile").with(user("test")))
                .andExpect(status().isForbidden());
    }
}
