package com.example.identifyservice.configuration;

import com.example.identifyservice.entity.ShippingRate;
import com.example.identifyservice.repository.ShippingRateRepository;
import com.example.identifyservice.util.VietnamProvinces;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShippingRateSeeder implements ApplicationRunner {
    private static final long METRO_FEE = 25_000;
    private static final long DEFAULT_FEE = 35_000;

    private final ShippingRateRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        for (String province : VietnamProvinces.ALL) {
            if (!repository.existsByProvinceIgnoreCase(province)) {
                boolean metro = province.equals("Hà Nội") || province.equals("TP Hồ Chí Minh");
                repository.save(ShippingRate.builder().province(province)
                        .fee(metro ? METRO_FEE : DEFAULT_FEE).build());
            }
        }
    }
}
