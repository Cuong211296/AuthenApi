package com.example.identifyservice.event;

/** Published once an order is confirmed for the customer (COD placed, or MoMo paid). */
public record OrderConfirmedEvent(String orderId) {
}
