package com.example.identifyservice.ghn;

/** Parsed GHN fee answer: {@code total} in VND, never negative. */
public record GhnFeeResult(long total) {
}
