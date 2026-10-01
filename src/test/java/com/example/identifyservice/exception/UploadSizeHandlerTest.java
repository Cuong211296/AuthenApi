package com.example.identifyservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class UploadSizeHandlerTest {
    @Test
    void containerLevelOversizeMapsToCleanApiResponse() {
        var response = new GlobalExceptionHandler().handlingUploadTooLarge(new MaxUploadSizeExceededException(7));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().getCode()).isEqualTo(2022);
        assertThat(response.getBody().getMessage()).contains("5 MB");
    }
}
