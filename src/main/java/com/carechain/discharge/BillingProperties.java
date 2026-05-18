package com.carechain.discharge;

import com.carechain.bed.model.WardType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "carechain.billing")
@Getter
@Setter
public class BillingProperties {

    private String currencyCode = "INR";
    private BigDecimal consultationFee = new BigDecimal("650");
    private BigDecimal dischargeProcessingFee = new BigDecimal("250");
    private BigDecimal generalDailyRate = new BigDecimal("2200");
    private BigDecimal icuDailyRate = new BigDecimal("9500");
    private BigDecimal emergencyDailyRate = new BigDecimal("4000");
    private BigDecimal maternityDailyRate = new BigDecimal("3500");

    public BigDecimal resolveDailyRate(WardType wardType) {
        return switch (wardType) {
            case ICU -> icuDailyRate;
            case EMERGENCY -> emergencyDailyRate;
            case MATERNITY -> maternityDailyRate;
            case GENERAL -> generalDailyRate;
        };
    }
}
