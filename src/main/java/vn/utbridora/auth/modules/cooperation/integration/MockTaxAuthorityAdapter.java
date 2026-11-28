package vn.utbridora.auth.modules.cooperation.integration;

import java.time.*;
import java.util.Map;
import vn.utbridora.auth.modules.cooperation.domain.TaxCodeValidator;
import vn.utbridora.auth.modules.cooperation.port.TaxAuthorityLookupPort;

/** Demonstration data, NOT an authoritative statement about any business's current tax status. */
public final class MockTaxAuthorityAdapter implements TaxAuthorityLookupPort {
    private final Clock clock;
    private final Map<String, String> names = Map.of("0101248141", "CÔNG TY CỔ PHẦN FPT", "0300588569", "CÔNG TY CỔ PHẦN SỮA VIỆT NAM",
        "0100109106", "TẬP ĐOÀN CÔNG NGHIỆP - VIỄN THÔNG QUÂN ĐỘI", "0100112437", "NGÂN HÀNG TMCP NGOẠI THƯƠNG VIỆT NAM",
        "0300000000", "DOANH NGHIỆP DEMO TẠM NGỪNG", "0300000018", "DOANH NGHIỆP DEMO GIẢI THỂ");
    public MockTaxAuthorityAdapter() { this(Clock.systemUTC()); }
    public MockTaxAuthorityAdapter(Clock clock) { this.clock = clock; }
    @Override public LookupResult lookup(String raw) {
        String code = TaxCodeValidator.normalize(raw), base = code.length() >= 10 ? code.substring(0, 10) : code;
        if (!TaxCodeValidator.validate(code).validEnterprise() || !names.containsKey(base) || code.contains("-") && !code.endsWith("-001"))
            return new NotFound(code, "mock", clock.instant());
        TaxpayerStatus status = base.equals("0300000000") ? TaxpayerStatus.SUSPENDED : base.equals("0300000018") ? TaxpayerStatus.DISSOLVED : TaxpayerStatus.ACTIVE;
        return new Found(code, names.get(base) + (code.contains("-") ? " - CHI NHÁNH 001" : ""),
            "123 Đường Minh Họa, Phường 1, Quận 1, TP. Hồ Chí Minh", "Cơ quan thuế minh họa",
            status, status == TaxpayerStatus.ACTIVE ? "Đang hoạt động (dữ liệu minh họa)" : status == TaxpayerStatus.SUSPENDED
                ? "Tạm ngừng (dữ liệu minh họa)" : "Giải thể (dữ liệu minh họa)", "mock", clock.instant());
    }
}
