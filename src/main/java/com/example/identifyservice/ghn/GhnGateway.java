package com.example.identifyservice.ghn;

import java.util.List;

public interface GhnGateway {
    /** @throws GhnUnavailableException when GHN cannot be reached or answers something unusable */
    GhnFeeResult calculateFee(GhnFeeRequest request);

    /** @throws GhnUnavailableException when GHN cannot be reached or answers something unusable */
    List<GhnProvince> provinces();

    List<GhnDistrict> districts(int provinceId);

    List<GhnWard> wards(int districtId);
}
