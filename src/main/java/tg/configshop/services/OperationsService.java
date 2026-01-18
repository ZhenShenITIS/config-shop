package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tg.configshop.dto.OperationView;
import tg.configshop.repositories.PurchaseRepository;

@Service
@RequiredArgsConstructor
public class OperationsService {
    private final PurchaseRepository purchaseRepository;
    private final int OPERATION_PAGE_SIZE = 10;


    public Page<OperationView> getOperationsByUserId (long userId, int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber, OPERATION_PAGE_SIZE);

        return purchaseRepository.findOperationsByUserId(userId, pageable);
    }
}
