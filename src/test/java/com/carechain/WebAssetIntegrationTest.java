package com.carechain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebAssetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageShouldReferenceLocalBootstrapAsset() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/webjars/bootstrap/5.3.3/css/bootstrap.min.css")));
    }

    @Test
    void packagedWebJarAssetsShouldBeServed() throws Exception {
        mockMvc.perform(get("/webjars/bootstrap/5.3.3/css/bootstrap.min.css"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/webjars/bootstrap-icons/1.11.3/font/bootstrap-icons.min.css"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/webjars/sockjs-client/1.5.1/sockjs.min.js"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/webjars/stomp-websocket/2.3.4/stomp.min.js"))
                .andExpect(status().isOk());
    }
}
