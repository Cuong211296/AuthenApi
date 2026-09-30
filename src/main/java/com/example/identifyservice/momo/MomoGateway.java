package com.example.identifyservice.momo;

public interface MomoGateway {
    MomoCreateResult create(MomoCreateCommand command);

    MomoQueryResult query(String providerOrderId, String requestId);
}
