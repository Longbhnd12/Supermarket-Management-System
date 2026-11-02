package com.sms.backend.service.payment;

import com.sms.backend.repository.SaleRepository;
import com.sms.backend.service.SaleCompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;

/** Durable outbox flag is committed with SUCCESS. Recovery never charges the customer. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SaleCompletionWorker {
    private final SaleRepository sales;
    private final SaleCompletionService completion;
    private int page;
    @Scheduled(fixedDelayString = "${sale.completion-delay-ms:5000}")
    public void recover() {
        var ids = sales.findCompletionRequests(PageRequest.of(page, 25));
        page = ids.size() == 25 ? page + 1 : 0;
        for (Long id : ids) {
            try { completion.complete(id); }
            catch (RuntimeException e) { log.warn("UC30 completion deferred for sale {}: {}", id, e.getMessage()); }
        }
    }
}
