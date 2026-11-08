package com.sms.backend.service.stock;

import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockReceiptDetail;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.NotificationType;
import com.sms.backend.enums.RoleName;
import com.sms.backend.repository.NotificationRepository;
import com.sms.backend.repository.StockReceiptDetailRepository;
import com.sms.backend.repository.SystemSettingRepository;
import com.sms.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockExpiryNotifier {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int DEFAULT_NEAR_EXPIRY_DAYS = 30;

    private final StockReceiptDetailRepository stockReceiptDetailRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final SystemSettingRepository systemSettingRepository;

    @Scheduled(cron = "${stock.expiry-check.cron:0 0 7 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void scheduledExpiryCheck() {
        runExpiryCheckNow();
    }

    public void runExpiryCheckNow() {
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        LocalDateTime startOfDay = today.atStartOfDay();

        boolean expiredAlreadySent = notificationRepository.existsRecentAlert(
                NotificationType.NEAR_EXPIRY, startOfDay, "%Expired Batches (" + today + ")%"
        );
        boolean nearExpiryAlreadySent = notificationRepository.existsRecentAlert(
                NotificationType.NEAR_EXPIRY, startOfDay, "%Near Expiry Batches (" + today + ")%"
        );

        if (expiredAlreadySent && nearExpiryAlreadySent) {
            log.info("Expiry check for {} already executed and notifications sent. Skipping duplicate run.", today);
            return;
        }

        int warningDays = getNearExpiryThresholdDays();
        LocalDate nearExpiryThreshold = today.plusDays(warningDays);

        List<StockReceiptDetail> expiringBatches = stockReceiptDetailRepository.findActiveExpiringBatches();
        List<StockReceiptDetail> expired = new ArrayList<>();
        List<StockReceiptDetail> nearExpiry = new ArrayList<>();

        for (StockReceiptDetail d : expiringBatches) {
            Product p = d.getProduct();
            if (p == null || p.getStatus() != GeneralStatus.ACTIVE) continue;
            LocalDate exp = d.getExpirationDate();
            if (exp == null) continue;

            if (exp.isBefore(today)) {
                expired.add(d);
            } else if (!exp.isAfter(nearExpiryThreshold)) {
                nearExpiry.add(d);
            }
        }

        if (!expired.isEmpty() && !expiredAlreadySent) {
            String title = "Stock Alert: Expired Batches (" + today + ")";
            String message = "There are " + expired.size() + " batch(es) that have expired as of " + today
                    + ". Please review and process inventory disposition.";
            notificationService.sendToRoles(
                    Set.of(RoleName.WAREHOUSE_STAFF, RoleName.MANAGER),
                    NotificationType.NEAR_EXPIRY,
                    title,
                    message
            );
            log.info("Sent expired stock alert for {} batches to warehouse staff and managers", expired.size());
        }

        if (!nearExpiry.isEmpty() && !nearExpiryAlreadySent) {
            String title = "Stock Alert: Near Expiry Batches (" + today + ")";
            String message = "There are " + nearExpiry.size() + " batch(es) nearing expiration within " + warningDays
                    + " days (as of " + today + "). Please review stock rotation or discount promotions.";
            notificationService.sendToRoles(
                    Set.of(RoleName.WAREHOUSE_STAFF, RoleName.MANAGER),
                    NotificationType.NEAR_EXPIRY,
                    title,
                    message
            );
            log.info("Sent near-expiry stock alert for {} batches to warehouse staff and managers", nearExpiry.size());
        }
    }

    private int getNearExpiryThresholdDays() {
        if (systemSettingRepository == null) return DEFAULT_NEAR_EXPIRY_DAYS;
        return systemSettingRepository.findBySettingKey("NEAR_EXPIRY_DAYS")
                .or(() -> systemSettingRepository.findBySettingKey("EXPIRY_WARNING_DAYS"))
                .or(() -> systemSettingRepository.findBySettingKey("NEAR_EXPIRY_WARNING_DAYS"))
                .or(() -> systemSettingRepository.findBySettingKey("EXPIRATION_WARNING_DAYS"))
                .map(s -> {
                    try {
                        return Integer.parseInt(s.getSettingValue().trim());
                    } catch (Exception e) {
                        return DEFAULT_NEAR_EXPIRY_DAYS;
                    }
                })
                .orElse(DEFAULT_NEAR_EXPIRY_DAYS);
    }
}

