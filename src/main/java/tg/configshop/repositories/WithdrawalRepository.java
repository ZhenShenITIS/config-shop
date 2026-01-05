package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.model.Withdrawal;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {
}
