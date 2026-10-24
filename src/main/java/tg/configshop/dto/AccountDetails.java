package tg.configshop.dto;
import java.util.UUID;
public record AccountDetails(UUID id, long balance, int referralPercentage1lvl,
                             int referralPercentage2lvl, int referralPercentage3lvl) {}
