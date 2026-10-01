package com.example.identifyservice.ghtk;

public interface GhtkGateway {
    /** @throws GhtkUnavailableException when GHTK cannot be reached or answers something unusable */
    GhtkFeeResult calculateFee(GhtkFeeRequest request);
}
