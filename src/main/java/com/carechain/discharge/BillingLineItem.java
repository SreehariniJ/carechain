package com.carechain.discharge;

import java.math.BigDecimal;

public record BillingLineItem(String label, BigDecimal amount) {
}
