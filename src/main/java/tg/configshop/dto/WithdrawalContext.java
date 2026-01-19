package tg.configshop.dto;

import lombok.Getter;
import lombok.Setter;
import tg.configshop.constants.WithdrawalType;

@Getter
@Setter
public class WithdrawalContext {
    WithdrawalType withdrawalType;
    long amount;
    String requisites;

}
