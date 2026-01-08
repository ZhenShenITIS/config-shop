package tg.configshop.external_api.pay.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.configshop.external_api.pay.model.PlategaPayment;

public interface PlategaPaymentRepository extends JpaRepository<PlategaPayment, String> {
}
