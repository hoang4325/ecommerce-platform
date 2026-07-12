package com.yashmerino.ecommerce.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.model.dto.CancelRequestDTO;
import com.yashmerino.ecommerce.model.dto.CheckoutRequestDTO;
import com.yashmerino.ecommerce.model.dto.CheckoutResponseDTO;
import com.yashmerino.ecommerce.model.dto.PaymentInitiationRequestDTO;
import com.yashmerino.ecommerce.model.dto.PaymentInitiationResponseDTO;
import com.yashmerino.ecommerce.services.CheckoutService;
import com.yashmerino.ecommerce.utils.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
class CheckoutControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CheckoutService checkoutService;

    @Test
    @WithMockUser(username = "user", authorities = {"USER"})
    void checkout_success() throws Exception {
        UUID key = UUID.randomUUID();
        CheckoutRequestDTO request = new CheckoutRequestDTO(0, null, "EUR");
        CheckoutResponseDTO response = new CheckoutResponseDTO(1L, 10L, new BigDecimal("100.00"), "EUR",
                PaymentStatus.AWAITING_PAYMENT_METHOD, Instant.now());

        when(checkoutService.checkout(any(UUID.class), any(CheckoutRequestDTO.class))).thenReturn(response);

        mvc.perform(post("/api/orders/checkout")
                        .header("Idempotency-Key", key.toString())
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.paymentId").value(10))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.paymentStatus").value("AWAITING_PAYMENT_METHOD"));
    }

    @Test
    @WithMockUser(username = "user", authorities = {"USER"})
    void checkout_missingIdempotencyKey_returnsBadRequest() throws Exception {
        CheckoutRequestDTO request = new CheckoutRequestDTO(0, null, "EUR");

        mvc.perform(post("/api/orders/checkout")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user", authorities = {"USER"})
    void checkout_invalidIdempotencyKey_returnsBadRequest() throws Exception {
        CheckoutRequestDTO request = new CheckoutRequestDTO(0, null, "EUR");

        mvc.perform(post("/api/orders/checkout")
                        .header("Idempotency-Key", "not-a-uuid")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user", authorities = {"USER"})
    void checkout_invalidBody_returnsBadRequest() throws Exception {
        UUID key = UUID.randomUUID();
        String invalidJson = "{\"requestedPoints\": -1}";

        mvc.perform(post("/api/orders/checkout")
                        .header("Idempotency-Key", key.toString())
                        .content(invalidJson)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkout_unauthenticated_returnsUnauthorized() throws Exception {
        UUID key = UUID.randomUUID();
        CheckoutRequestDTO request = new CheckoutRequestDTO(0, null, "EUR");

        mvc.perform(post("/api/orders/checkout")
                        .header("Idempotency-Key", key.toString())
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", authorities = {"USER"})
    void initiate_success() throws Exception {
        UUID key = UUID.randomUUID();
        PaymentInitiationRequestDTO request = new PaymentInitiationRequestDTO("pm_test_123");
        PaymentInitiationResponseDTO response = new PaymentInitiationResponseDTO(1L, 10L,
                new BigDecimal("100.00"), "EUR", PaymentStatus.PENDING);

        when(checkoutService.initiate(any(Long.class), any(UUID.class), any(PaymentInitiationRequestDTO.class)))
                .thenReturn(response);

        mvc.perform(post("/api/payments/10/initiate")
                        .header("Idempotency-Key", key.toString())
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.paymentId").value(10))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));
    }

    @Test
    void initiate_unauthenticated_returnsUnauthorized() throws Exception {
        UUID key = UUID.randomUUID();
        PaymentInitiationRequestDTO request = new PaymentInitiationRequestDTO("pm_test_123");

        mvc.perform(post("/api/payments/10/initiate")
                        .header("Idempotency-Key", key.toString())
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", authorities = {"USER"})
    void initiate_invalidPaymentMethodId_returnsBadRequest() throws Exception {
        UUID key = UUID.randomUUID();
        String invalidJson = "{\"paymentMethodId\": \"invalid\"}";

        mvc.perform(post("/api/payments/10/initiate")
                        .header("Idempotency-Key", key.toString())
                        .content(invalidJson)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user", authorities = {"USER"})
    void initiate_emptyBody_returnsBadRequest() throws Exception {
        UUID key = UUID.randomUUID();

        mvc.perform(post("/api/payments/10/initiate")
                        .header("Idempotency-Key", key.toString())
                        .content("")
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"USER"})
    void cancelOrder_ShouldReturn200() throws Exception {
        CancelRequestDTO request = new CancelRequestDTO("change my mind");

        mvc.perform(post("/api/orders/1/cancel")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"USER"})
    void cancelOrder_WithInvalidIdempotencyKey_ShouldReturn400() throws Exception {
        CancelRequestDTO request = new CancelRequestDTO("test");

        mvc.perform(post("/api/orders/1/cancel")
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"USER"})
    void cancelOrder_WithBlankReason_ShouldReturn400() throws Exception {
        CancelRequestDTO request = new CancelRequestDTO("");

        mvc.perform(post("/api/orders/1/cancel")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelOrder_WithoutAuth_ShouldReturn403() throws Exception {
        CancelRequestDTO request = new CancelRequestDTO("test");

        mvc.perform(post("/api/orders/1/cancel")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }
}
