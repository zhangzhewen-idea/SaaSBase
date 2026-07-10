package com.saasbase.api;

import com.saasbase.SaaSBaseApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SaaSBaseApplication.class)
class ApiPathPartitionTest {

    @Autowired
    WebApplicationContext context;

    @Test
    void open_api_reserved_path_is_not_implemented_in_phase_one() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        mockMvc.perform(get("/api/v1/open/ping"))
                .andExpect(status().isNotFound());
    }
}
