package com.example.identifyservice.service;

/** Admin on/off switches of the carriers. A carrier quotes only when it is configured in .env AND switched on. */
public record CarrierSwitches(boolean ghn, boolean ghtk) {
    public static final CarrierSwitches ALL_ON = new CarrierSwitches(true, true);
}
