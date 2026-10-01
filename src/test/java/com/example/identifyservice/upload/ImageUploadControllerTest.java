package com.example.identifyservice.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.example.identifyservice.testsupport.FakeImageStorage;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ImageUploadControllerTest {
    static final String URL = "/admin/uploads/image";

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 1, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    static final byte[] GIF = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PDF = "%PDF-1.4 ....".getBytes(StandardCharsets.US_ASCII);
    static final byte[] TEXT = "hello, not an image".getBytes(StandardCharsets.US_ASCII);

    @Autowired MockMvc mvc;
    @Autowired FakeImageStorage storage;

    @BeforeEach
    void setUp() {
        storage.reset();
    }

    @AfterEach
    void tearDown() {
        storage.reset();
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("boss")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject("bob")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private ResultActions post(String filename, String contentType, byte[] body, RequestPostProcessor who)
            throws Exception {
        return mvc.perform(multipart(URL).file(new MockMultipartFile("file", filename, contentType, body)).with(who));
    }

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(multipart(URL).file(new MockMultipartFile("file", "a.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
        assertThat(storage.calls).isEmpty();
    }

    @Test
    void plainUserGets403() throws Exception {
        post("a.png", "image/png", PNG, user()).andExpect(status().isForbidden());
        assertThat(storage.calls).isEmpty();
    }

    @Test
    void adminUploadsEachSupportedFormatByMagicBytes() throws Exception {
        post("a.png", "image/png", PNG, admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.url").value(FakeImageStorage.URL));
        post("whatever.bin", "application/octet-stream", JPEG, admin()).andExpect(status().isOk());
        post("x.txt", "text/plain", WEBP, admin()).andExpect(status().isOk());
        assertThat(storage.calls).extracting(FakeImageStorage.Call::contentType)
                .containsExactly("image/png", "image/jpeg", "image/webp");
    }

    @Test
    void invalidContentsAreRejectedBeforeStorage() throws Exception {
        for (byte[] bad : new byte[][] {GIF, PDF, TEXT}) {
            post("pic.png", "image/png", bad, admin()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(2021));
        }
        post("fake.jpg", "image/jpeg", TEXT, admin()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2021));
        assertThat(storage.calls).isEmpty();
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        post("a.png", "image/png", new byte[0], admin()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
        assertThat(storage.calls).isEmpty();
    }

    @Test
    void missingPartIsRejectedCleanly() throws Exception {
        mvc.perform(multipart(URL).with(admin())).andExpect(status().is4xxClientError());
        assertThat(storage.calls).isEmpty();
    }

    @Test
    void tooBigFileIsRejectedWithCleanError() throws Exception {
        byte[] big = new byte[(int) ImageUploadService.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        post("big.png", "image/png", big, admin()).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(2022))
                .andExpect(jsonPath("$.message").value("Ảnh vượt quá dung lượng tối đa 5 MB"));
        assertThat(storage.calls).isEmpty();
    }

    @Test
    void exactlyFiveMegabytesIsAccepted() throws Exception {
        byte[] max = new byte[(int) ImageUploadService.MAX_BYTES];
        System.arraycopy(PNG, 0, max, 0, PNG.length);
        post("max.png", "image/png", max, admin()).andExpect(status().isOk());
        assertThat(storage.calls).hasSize(1);
    }

    @Test
    void disabledWhenNotConfigured() throws Exception {
        storage.setEnabled(false);
        post("a.png", "image/png", PNG, admin()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(2020));
        assertThat(storage.calls).isEmpty();
    }

    @Test
    void storageFailureIsMappedAndLeaksNothing(CapturedOutput output) throws Exception {
        storage.answer(() -> {
            throw new ImageStorageException("boom api_secret=TOP-SECRET-VALUE");
        });
        String body = post("a.png", "image/png", PNG, admin()).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(2023)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("TOP-SECRET-VALUE");
        assertThat(output.getAll()).doesNotContain("TOP-SECRET-VALUE");
    }

    @Test
    void nonHttpsOrOverlongUrlFromStorageIsRejected() throws Exception {
        storage.answer(() -> "http://res.cloudinary.com/demo/a.png");
        post("a.png", "image/png", PNG, admin()).andExpect(status().isBadGateway());
        storage.answer(() -> "https://res.cloudinary.com/" + "a".repeat(500));
        post("a.png", "image/png", PNG, admin()).andExpect(status().isBadGateway());
    }

    @Test
    void realCloudinaryStorageIsDisabledWhenAnyPropertyIsBlank() {
        assertThat(new CloudinaryProperties("c", "k", "").isEnabled()).isFalse();
        assertThat(new CloudinaryProperties("", "k", "s").isEnabled()).isFalse();
        assertThat(new CloudinaryProperties(null, null, null).isEnabled()).isFalse();
        assertThat(new CloudinaryProperties("c", "k", "s").isEnabled()).isTrue();
        CloudinaryImageStorage real = new CloudinaryImageStorage(new CloudinaryProperties("", "", ""));
        assertThat(real.isEnabled()).isFalse();
        org.junit.jupiter.api.Assertions.assertThrows(ImageStorageException.class,
                () -> real.upload(PNG, "image/png"));
    }

    @Test
    void propertiesToStringDoesNotExposeSecrets() {
        String text = new CloudinaryProperties("cloudname", "apikey123", "apisecret456").toString();
        assertThat(text).doesNotContain("apikey123").doesNotContain("apisecret456");
    }
}
