package com.example.identifyservice.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps the 63 pre-July-2025 provinces (still the spelling of GHN master data) to the 34 units of
 * {@link VietnamProvinces} that hold the shipping table. Names are matched ignoring case, diacritics, the prefixes
 * "TP." / "TP" / "Thành phố" / "Tỉnh", dots and extra spaces.
 */
public final class VietnamOldProvinces {
    private VietnamOldProvinces() {
    }

    /** new unit (table spelling) followed by the old provinces merged into it (unchanged units list themselves). */
    private static final String[][] GROUPS = {
            {"Hà Nội", "Hà Nội"},
            {"Huế", "Thừa Thiên Huế"},
            {"Lai Châu", "Lai Châu"},
            {"Điện Biên", "Điện Biên"},
            {"Sơn La", "Sơn La"},
            {"Lạng Sơn", "Lạng Sơn"},
            {"Quảng Ninh", "Quảng Ninh"},
            {"Thanh Hóa", "Thanh Hóa"},
            {"Nghệ An", "Nghệ An"},
            {"Hà Tĩnh", "Hà Tĩnh"},
            {"Cao Bằng", "Cao Bằng"},
            {"Tuyên Quang", "Hà Giang", "Tuyên Quang"},
            {"Lào Cai", "Yên Bái", "Lào Cai"},
            {"Thái Nguyên", "Bắc Kạn", "Thái Nguyên"},
            {"Phú Thọ", "Vĩnh Phúc", "Hòa Bình", "Phú Thọ"},
            {"Bắc Ninh", "Bắc Giang", "Bắc Ninh"},
            {"Hưng Yên", "Thái Bình", "Hưng Yên"},
            {"Hải Phòng", "Hải Dương", "Hải Phòng"},
            {"Ninh Bình", "Hà Nam", "Nam Định", "Ninh Bình"},
            {"Quảng Trị", "Quảng Bình", "Quảng Trị"},
            {"Đà Nẵng", "Quảng Nam", "Đà Nẵng"},
            {"Quảng Ngãi", "Kon Tum", "Quảng Ngãi"},
            {"Gia Lai", "Bình Định", "Gia Lai"},
            {"Khánh Hòa", "Ninh Thuận", "Khánh Hòa"},
            {"Lâm Đồng", "Đắk Nông", "Bình Thuận", "Lâm Đồng"},
            {"Đắk Lắk", "Phú Yên", "Đắk Lắk"},
            {"TP Hồ Chí Minh", "Bình Dương", "Bà Rịa - Vũng Tàu", "Hồ Chí Minh"},
            {"Đồng Nai", "Bình Phước", "Đồng Nai"},
            {"Tây Ninh", "Long An", "Tây Ninh"},
            {"Cần Thơ", "Sóc Trăng", "Hậu Giang", "Cần Thơ"},
            {"Vĩnh Long", "Bến Tre", "Trà Vinh", "Vĩnh Long"},
            {"Đồng Tháp", "Tiền Giang", "Đồng Tháp"},
            {"Cà Mau", "Bạc Liêu", "Cà Mau"},
            {"An Giang", "Kiên Giang", "An Giang"},
    };

    private static final Map<String, String> NEW_UNIT_BY_NORMALIZED_OLD = new LinkedHashMap<>();
    private static final List<String> OLD_NAMES = new ArrayList<>();

    static {
        for (String[] group : GROUPS) {
            for (int i = 1; i < group.length; i++) {
                OLD_NAMES.add(group[i]);
                NEW_UNIT_BY_NORMALIZED_OLD.put(normalize(group[i]), group[0]);
            }
        }
        // the new spellings themselves ("TP Hồ Chí Minh", "Huế") are accepted too
        for (String unit : VietnamProvinces.ALL) NEW_UNIT_BY_NORMALIZED_OLD.putIfAbsent(normalize(unit), unit);
    }

    /** The 63 old province names (canonical spelling). */
    public static List<String> oldNames() {
        return List.copyOf(OLD_NAMES);
    }

    /** The current unit (table spelling) an old or new province name belongs to, empty when unknown. */
    public static Optional<String> newUnitOf(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return Optional.ofNullable(NEW_UNIT_BY_NORMALIZED_OLD.get(normalize(name)));
    }

    static String normalize(String name) {
        String s = Normalizer.normalize(name.trim().toLowerCase(java.util.Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd');
        s = s.replaceAll("[^a-z0-9]+", " ").trim();
        return s.replaceFirst("^(thanh pho|tinh|tp) ", "");
    }
}
