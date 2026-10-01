package com.example.identifyservice.testsupport;

import com.example.identifyservice.ghtk.GhtkFeeRequest;
import com.example.identifyservice.ghtk.GhtkFeeResult;
import com.example.identifyservice.ghtk.GhtkGateway;
import com.example.identifyservice.ghtk.GhtkUnavailableException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Replaces the real GHTK gateway in every Spring test. By default it behaves like an unreachable GHTK, so tests
 * that do not care about shipping keep using the fixed province table.
 */
@Component
@Primary
public class FakeGhtkGateway implements GhtkGateway {
    public volatile Function<GhtkFeeRequest, GhtkFeeResult> handler = r -> {
        throw new GhtkUnavailableException("fake GHTK is down");
    };
    public final List<GhtkFeeRequest> calls = new CopyOnWriteArrayList<>();

    public void returnFee(long fee) {
        handler = r -> new GhtkFeeResult(true, true, fee, null);
    }

    public void returnResult(GhtkFeeResult result) {
        handler = r -> result;
    }

    public void fail(RuntimeException e) {
        handler = r -> {
            throw e;
        };
    }

    public void reset() {
        handler = r -> {
            throw new GhtkUnavailableException("fake GHTK is down");
        };
        calls.clear();
    }

    @Override
    public GhtkFeeResult calculateFee(GhtkFeeRequest request) {
        calls.add(request);
        return handler.apply(request);
    }
}
