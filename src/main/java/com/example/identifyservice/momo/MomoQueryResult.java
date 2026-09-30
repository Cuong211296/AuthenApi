package com.example.identifyservice.momo;

public record MomoQueryResult(int resultCode, String message, long amount, Long transId, String raw) {
}
