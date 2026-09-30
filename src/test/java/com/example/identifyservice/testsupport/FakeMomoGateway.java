package com.example.identifyservice.testsupport;

import com.example.identifyservice.momo.MomoCreateCommand;
import com.example.identifyservice.momo.MomoCreateResult;
import com.example.identifyservice.momo.MomoGateway;
import com.example.identifyservice.momo.MomoQueryResult;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Replaces the real MoMo gateway in every Spring test. */
@Component
@Primary
public class FakeMomoGateway implements MomoGateway {
    public volatile MomoCreateResult createResult = new MomoCreateResult(0, "Successful.", "https://pay.example/checkout");
    public volatile Function<String, MomoQueryResult> queryHandler = id -> pending();
    public volatile RuntimeException queryFailure;
    public final List<MomoCreateCommand> creates = new CopyOnWriteArrayList<>();

    public static MomoQueryResult pending() {
        return new MomoQueryResult(1000, "pending", -1, null, "{}");
    }

    public static MomoQueryResult paid(long amount) {
        return new MomoQueryResult(0, "Successful.", amount, 555L, "{\"resultCode\":0}");
    }

    public void reset() {
        createResult = new MomoCreateResult(0, "Successful.", "https://pay.example/checkout");
        queryHandler = id -> pending();
        queryFailure = null;
        creates.clear();
    }

    @Override
    public MomoCreateResult create(MomoCreateCommand command) {
        creates.add(command);
        return createResult;
    }

    @Override
    public MomoQueryResult query(String providerOrderId, String requestId) {
        if (queryFailure != null) throw queryFailure;
        return queryHandler.apply(providerOrderId);
    }
}
