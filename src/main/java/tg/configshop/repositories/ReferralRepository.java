package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.model.Referral;

public interface ReferralRepository extends JpaRepository<Referral, Long> {
}
