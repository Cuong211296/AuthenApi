package com.example.identifyservice.testsupport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.example.identifyservice.upload.ImageStorage;

/** Replaces the real Cloudinary storage in every Spring test. Records calls; call {@link #reset()} in setUp/tearDown. */
@Component
@Primary
public class FakeImageStorage implements ImageStorage {
    public record Call(byte[] data, String contentType) {}

    public static final String URL = "https://res.cloudinary.com/demo/image/upload/v1/quinibear/products/fake.png";

    public final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile boolean enabled = true;
    private volatile Supplier<String> behaviour = () -> URL;

    public void reset() {
        calls.clear();
        enabled = true;
        behaviour = () -> URL;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void answer(Supplier<String> behaviour) {
        this.behaviour = behaviour;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String upload(byte[] data, String contentType) {
        calls.add(new Call(data, contentType));
        return behaviour.get();
    }
}
