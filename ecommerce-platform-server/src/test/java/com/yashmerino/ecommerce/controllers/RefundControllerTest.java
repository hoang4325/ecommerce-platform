package com.yashmerino.ecommerce.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.domain.Refund;
import com.yashmerino.ecommerce.model.dto.RefundRequestDTO;
import com.yashmerino.ecommerce.services.RefundService;
import com.yashmerino.ecommerce.services.interfaces.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
class RefundControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RefundService refundService;

    @MockBean
    private UserService userService;

    @Test
    @WithMockUser(username = "user", authorities = {"USER"})
    void requestRefund_WithValidBody_Returns201() throws Exception {
        RefundRequestDTO dto = new RefundRequestDTO("item not as described");
        Refund refund = new Refund();
        refund.setId(1L);

        User user = new User();
        user.setId(1L);

        when(userService.getByUsername("user")).thenReturn(user);
        when(refundService.requestRefund(eq(1L), eq(1L), any(RefundRequestDTO.class))).thenReturn(refund);

        mvc.perform(post("/api/orders/1/refund")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "user", authorities = {"USER"})
    void requestRefund_WithBlankReason_Returns400() throws Exception {
        String invalidJson = "{\"reason\": \"\"}";

        mvc.perform(post("/api/orders/1/refund")
                        .contentType(APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user", authorities = {"USER"})
    void requestRefund_WithNullReason_Returns400() throws Exception {
        String invalidJson = "{\"reason\": null}";

        mvc.perform(post("/api/orders/1/refund")
                        .contentType(APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestRefund_Unauthenticated_Returns4xx() throws Exception {
        RefundRequestDTO dto = new RefundRequestDTO("reason");

        mvc.perform(post("/api/orders/1/refund")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().is4xxClientError());
    }
}
