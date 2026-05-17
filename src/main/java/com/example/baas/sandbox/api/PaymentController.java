package com.example.baas.sandbox.api;

import com.example.baas.sandbox.payment.PaymentEvent;
import com.example.baas.sandbox.payment.PaymentRequest;
import com.example.baas.sandbox.payment.PaymentResponse;
import com.example.baas.sandbox.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Create an A2A payment")
    ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Sandbox-Scenario", required = false) String sandboxScenario
    ) {
        PaymentService.CreateResult result = paymentService.create(request, idempotencyKey, sandboxScenario);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.response());
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment status")
    PaymentResponse getPayment(@PathVariable String paymentId) {
        return paymentService.get(paymentId);
    }

    @GetMapping("/{paymentId}/events")
    @Operation(summary = "Get payment lifecycle events")
    List<PaymentEvent> getPaymentEvents(@PathVariable String paymentId) {
        return paymentService.events(paymentId);
    }
}
