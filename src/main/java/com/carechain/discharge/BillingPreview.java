package com.carechain.discharge;

import java.math.BigDecimal;
import java.util.List;

public record BillingPreview(
        Long admissionId,
        long stayDays,
        String currencyCode,
        BigDecimal dailyRate,
        List<BillingLineItem> lineItems,
        BigDecimal totalAmount
) {
}
