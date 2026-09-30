package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.response.MomoPayResponse;
import com.example.identifyservice.dto.response.PaymentResultResponse;
import com.example.identifyservice.momo.MomoIpnRequest;
import com.example.identifyservice.service.PaymentService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentController {
    PaymentService paymentService;

    @PostMapping("/orders/{code}/pay/momo")
    ApiResponse<MomoPayResponse> payWithMomo(@PathVariable String code) {
        return ApiResponse.ok(paymentService.startMomoPayment(code));
    }

    @GetMapping("/payments/momo/return")
    ApiResponse<PaymentResultResponse> momoReturn(@RequestParam String orderCode) {
        return ApiResponse.ok(paymentService.confirmMomoReturn(orderCode));
    }

    @PostMapping("/payments/momo/ipn")
    ResponseEntity<Void> momoIpn(@RequestBody MomoIpnRequest request) {
        paymentService.handleIpn(request);
        return ResponseEntity.noContent().build();
    }
}
