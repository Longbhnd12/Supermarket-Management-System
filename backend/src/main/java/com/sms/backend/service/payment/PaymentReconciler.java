// PaymentReconciler - reconciles pending and completed transaction logs
package com.sms.backend.service.payment;

import com.sms.backend.repository.PaymentRepository;
import com.sms.backend.service.serviceImpl.PaymentServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDateTime;
import com.sms.backend.enums.PaymentMethod;
import com.sms.backend.enums.PaymentStatus;
import com.sms.backend.exception.BadRequestException;
import java.util.stream.Stream;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class PaymentReconciler {
    private final PaymentRepository payments;
    private final PaymentServiceImpl service;
    private final PaymentGateway gateway;
    private final Clock clock;
    private long lastProcessedId;
    private final ConcurrentHashMap<Long, Instant> queryTimes = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${payment.reconcile-delay-ms:60000}", initialDelayString = "${payment.reconcile-delay-ms:60000}")
    public void sweep() {
        // Bounded work per sweep; ambiguous network failures leave the sale locked, never silently retry a charge.
        var ids = payments.findExpiredIds(LocalDateTime.now(clock)).stream().sorted().toList();
        var batch = Stream.concat(ids.stream().filter(id -> id > lastProcessedId), ids.stream().filter(id -> id <= lastProcessedId))
                .limit(25).toList();
        batch.forEach(id -> {
            lastProcessedId = id;
            try { reconcile(id); }
            catch (RuntimeException ignored) { /* Persisted TIMEOUT + reconciliationRequired remains visible to operator. */ }
        });
    }
    public void reconcile(Long id) {
        service.expire(id);
        var snapshot = service.snapshot(id);
        if (snapshot.getPaymentMethod() == PaymentMethod.CASH || snapshot.getPaymentStatus() == PaymentStatus.SUCCESS && !snapshot.isReconciliationRequired()) {
            throw new BadRequestException("This payment does not require gateway reconciliation");
        }
        Instant now = clock.instant();
        queryTimes.entrySet().removeIf(entry -> entry.getValue().isBefore(now.minus(Duration.ofMinutes(30))));
        boolean[] allowed = {false};
        queryTimes.compute(id, (key, last) -> {
            if (last == null || !last.plus(Duration.ofMinutes(5)).isAfter(now)) { allowed[0] = true; return now; }
            return last;
        });
        if (!allowed[0]) throw new BadRequestException("VNPay query was sent recently. Wait 5 minutes before another reconciliation");
        service.apply(gateway.query(snapshot));
    }
}
