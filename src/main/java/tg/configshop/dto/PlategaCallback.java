package tg.configshop.dto;

import java.math.BigDecimal;

public record PlategaCallback(String providerPaymentId, BigDecimal amount, String currency, String status,
                              Integer paymentMethod, String payload) {}
