package tg.configshop.dto;
import tg.configshop.constants.WithdrawalType;
public record WithdrawalRequest(WithdrawalType type, long amount, String requisites) {}
