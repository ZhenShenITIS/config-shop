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
    private final AccountService accounts;


    public Page<OperationView> getOperations(java.util.UUID accountId, int pageNumber, int pageSize) {
        accounts.requireAccount(accountId);
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid page size");
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        return purchaseRepository.findOperationsByAccountId(accountId, pageable);
    }
}
