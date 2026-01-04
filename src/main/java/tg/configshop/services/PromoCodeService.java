package tg.configshop.services;

import tg.configshop.exceptions.PromoCodeAlreadyUsedException;
import tg.configshop.exceptions.PromoCodeEndedException;
import tg.configshop.exceptions.PromoCodeNotFoundException;
import tg.configshop.exceptions.ReferralPromoCodeAlreadyUsedException;
import tg.configshop.model.PromoCode;

public interface PromoCodeService {
    void activatePromoCode (String code, Long userId) throws PromoCodeAlreadyUsedException, PromoCodeEndedException, PromoCodeNotFoundException, ReferralPromoCodeAlreadyUsedException;
    void createPromoCode (PromoCode promoCode);

}
