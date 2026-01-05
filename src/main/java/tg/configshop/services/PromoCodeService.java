package tg.configshop.services;

import tg.configshop.exceptions.promocode.PromoCodeAlreadyUsedException;
import tg.configshop.exceptions.promocode.PromoCodeEndedException;
import tg.configshop.exceptions.promocode.PromoCodeNotFoundException;
import tg.configshop.exceptions.promocode.ReferralPromoCodeAlreadyUsedException;
import tg.configshop.model.PromoCode;

public interface PromoCodeService {
    void activatePromoCode (String code, Long userId) throws PromoCodeAlreadyUsedException, PromoCodeEndedException, PromoCodeNotFoundException, ReferralPromoCodeAlreadyUsedException;
    void createPromoCode (PromoCode promoCode);

}
