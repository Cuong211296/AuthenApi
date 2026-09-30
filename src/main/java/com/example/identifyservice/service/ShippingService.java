package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.ShippingRateRequest;
import com.example.identifyservice.dto.response.ShippingFeeResponse;
import com.example.identifyservice.dto.response.ShippingRateResponse;
import com.example.identifyservice.entity.ShippingRate;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.ShippingRateRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShippingService {
    ShippingRateRepository repository;

    @Transactional(readOnly = true)
    public ShippingRate requireRate(String province) {
        if (province == null || province.isBlank()) throw new AppException(ErrorCode.INVALID_PROVINCE);
        return repository.findByProvinceIgnoreCase(province.trim())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_PROVINCE));
    }

    @Transactional(readOnly = true)
    public ShippingFeeResponse fee(String province) {
        ShippingRate rate = requireRate(province);
        return new ShippingFeeResponse(rate.getProvince(), rate.getFee());
    }

    @Transactional(readOnly = true)
    public List<ShippingRateResponse> list() {
        return repository.findAllByOrderByProvinceAsc().stream().map(ShippingRateResponse::from).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ShippingRateResponse create(ShippingRateRequest request) {
        if (repository.existsByProvinceIgnoreCase(request.province().trim()))
            throw new AppException(ErrorCode.SLUG_EXISTED);
        return ShippingRateResponse.from(repository.save(
                ShippingRate.builder().province(request.province().trim()).fee(request.fee()).build()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ShippingRateResponse updateFee(String id, long fee) {
        if (fee < 0) throw new AppException(ErrorCode.INVALID_INPUT);
        ShippingRate rate = repository.findById(id).orElseThrow(() -> new AppException(ErrorCode.INVALID_PROVINCE));
        rate.setFee(fee);
        return ShippingRateResponse.from(repository.save(rate));
    }
}
