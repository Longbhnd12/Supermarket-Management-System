package com.sms.backend.service;

import com.sms.backend.entity.Product;
import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.Role;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.entity.StockReceiptDetail;
import com.sms.backend.entity.Supplier;
import com.sms.backend.entity.User;
import com.sms.backend.entity.Notification;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.NotificationType;
import com.sms.backend.enums.PurchaseOrderStatus;
import com.sms.backend.enums.RoleName;
import com.sms.backend.repository.NotificationRepository;
import com.sms.backend.repository.StockReceiptDetailRepository;
import com.sms.backend.repository.SystemSettingRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.service.serviceImpl.NotificationServiceImpl;
import com.sms.backend.service.stock.StockExpiryNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NotificationServiceUnitTest {

    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private NotificationServiceImpl notificationService;

    private StockReceiptDetailRepository stockReceiptDetailRepository;
    private SystemSettingRepository systemSettingRepository;
    private StockExpiryNotifier stockExpiryNotifier;

    private User managerUser1;
    private User managerUser2;
    private User warehouseUser;
    private User supplierUser;
    private Supplier testSupplier;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = new NotificationServiceImpl(notificationRepository, userRepository);

        stockReceiptDetailRepository = mock(StockReceiptDetailRepository.class);
        systemSettingRepository = mock(SystemSettingRepository.class);
        stockExpiryNotifier = new StockExpiryNotifier(
                stockReceiptDetailRepository,
                notificationRepository,
                notificationService,
                systemSettingRepository
        );

        Role managerRole = new Role();
        managerRole.setRoleId(1L);
        managerRole.setRoleName(RoleName.MANAGER);

        Role warehouseRole = new Role();
        warehouseRole.setRoleId(2L);
        warehouseRole.setRoleName(RoleName.WAREHOUSE_STAFF);

        Role supplierRole = new Role();
        supplierRole.setRoleId(3L);
        supplierRole.setRoleName(RoleName.SUPPLIER);

        managerUser1 = new User();
        managerUser1.setUserId(101L);
        managerUser1.setUsername("manager_alice");
        managerUser1.setRole(managerRole);
        managerUser1.setStatus(AccountStatus.ACTIVE);

        managerUser2 = new User();
        managerUser2.setUserId(102L);
        managerUser2.setUsername("manager_bob");
        managerUser2.setRole(managerRole);
        managerUser2.setStatus(AccountStatus.ACTIVE);

        warehouseUser = new User();
        warehouseUser.setUserId(201L);
        warehouseUser.setUsername("warehouse_charlie");
        warehouseUser.setRole(warehouseRole);
        warehouseUser.setStatus(AccountStatus.ACTIVE);

        testSupplier = new Supplier();
        testSupplier.setSupplierId(50L);
        testSupplier.setSupplierName("Dairy Farm Co");

        supplierUser = new User();
        supplierUser.setUserId(301L);
        supplierUser.setUsername("supplier_dairy");
        supplierUser.setRole(supplierRole);
        supplierUser.setSupplier(testSupplier);
        supplierUser.setStatus(AccountStatus.ACTIVE);
    }

    // =========================================================================
    // 1. sendToUser & Per-user Read Independence
    // =========================================================================

    @Test
    @DisplayName("sendToUser saves an unread notification with user entity and null targetRole")
    void testSendToUser_Success() {
        notificationService.sendToUser(managerUser1, NotificationType.PO_ALERT, "Test Title", "Test Message");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(managerUser1, saved.getUser());
        assertNull(saved.getTargetRole(), "Target role must be null for per-user notification");
        assertEquals(NotificationType.PO_ALERT, saved.getType());
        assertEquals("Test Title", saved.getTitle());
        assertEquals("Test Message", saved.getMessage());
        assertFalse(saved.getIsRead());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    @DisplayName("sendToUser truncates title exceeding 200 characters to prevent database errors")
    void testSendToUser_TruncatesLongTitle() {
        String longTitle = "A".repeat(250);
        notificationService.sendToUser(managerUser1, NotificationType.PO_ALERT, longTitle, "Short Message");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals(200, captor.getValue().getTitle().length());
    }

    @Test
    @DisplayName("sendToUser skips inactive or null user safely")
    void testSendToUser_InactiveOrNullUser() {
        notificationService.sendToUser(null, NotificationType.PO_ALERT, "Title", "Message");
        verify(notificationRepository, never()).save(any());

        User inactive = new User();
        inactive.setUsername("disabled_user");
        inactive.setStatus(AccountStatus.INACTIVE);

        notificationService.sendToUser(inactive, NotificationType.PO_ALERT, "Title", "Message");
        verify(notificationRepository, never()).save(any());
    }

    // =========================================================================
    // 2. sendToRole & sendToRoles
    // =========================================================================

    @Test
    @DisplayName("sendToRole creates individual notification records for all active role users")
    void testSendToRole_CreatesIndividualNotifications() {
        when(userRepository.findByRole_RoleNameAndStatus(RoleName.MANAGER, AccountStatus.ACTIVE))
                .thenReturn(List.of(managerUser1, managerUser2));

        notificationService.sendToRole(RoleName.MANAGER, NotificationType.PO_ALERT, "PR Submitted", "PR-001 needs review");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        List<Notification> savedList = captor.getAllValues();
        assertEquals(2, savedList.size());
        assertEquals(managerUser1, savedList.get(0).getUser());
        assertEquals(managerUser2, savedList.get(1).getUser());
        savedList.forEach(n -> {
            assertNull(n.getTargetRole());
            assertFalse(n.getIsRead());
            assertEquals("PR Submitted", n.getTitle());
        });
    }

    @Test
    @DisplayName("sendToRoles aggregates users across roles and deduplicates by user ID")
    void testSendToRoles_AggregatesAndDeduplicates() {
        when(userRepository.findByRole_RoleNameAndStatus(RoleName.MANAGER, AccountStatus.ACTIVE))
                .thenReturn(List.of(managerUser1));
        when(userRepository.findByRole_RoleNameAndStatus(RoleName.WAREHOUSE_STAFF, AccountStatus.ACTIVE))
                .thenReturn(List.of(warehouseUser));

        notificationService.sendToRoles(
                Set.of(RoleName.MANAGER, RoleName.WAREHOUSE_STAFF),
                NotificationType.LOW_STOCK,
                "Stock Alert",
                "Product ABC is low"
        );

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        List<User> recipients = captor.getAllValues().stream().map(Notification::getUser).toList();
        assertTrue(recipients.contains(managerUser1));
        assertTrue(recipients.contains(warehouseUser));
    }

    // =========================================================================
    // 3. Supplier Notification Privacy
    // =========================================================================

    @Test
    @DisplayName("sendToSupplier sends notification exclusively to the linked supplier user account")
    void testSendToSupplier_Success() {
        when(userRepository.findBySupplier_SupplierId(50L)).thenReturn(Optional.of(supplierUser));

        notificationService.sendToSupplier(50L, NotificationType.PO_ALERT, "PO Issued: PO-2026-001", "Details");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(supplierUser, saved.getUser());
        assertNull(saved.getTargetRole(), "Must never broadcast to targetRole='SUPPLIER'");
    }

    @Test
    @DisplayName("sendToSupplier does not broadcast when supplier account is missing or inactive")
    void testSendToSupplier_MissingOrInactive() {
        when(userRepository.findBySupplier_SupplierId(999L)).thenReturn(Optional.empty());

        notificationService.sendToSupplier(999L, NotificationType.PO_ALERT, "PO Issued: PO-999", "Details");
        verify(notificationRepository, never()).save(any());

        User inactiveSupplier = new User();
        inactiveSupplier.setUsername("suspended_supplier");
        inactiveSupplier.setStatus(AccountStatus.LOCKED);
        when(userRepository.findBySupplier_SupplierId(50L)).thenReturn(Optional.of(inactiveSupplier));

        notificationService.sendToSupplier(50L, NotificationType.PO_ALERT, "PO Issued: PO-50", "Details");
        verify(notificationRepository, never()).save(any());
    }

    // =========================================================================
    // 4. Stock Receipt Confirmation Notification
    // =========================================================================

    @Test
    @DisplayName("sendStockReceiptConfirmed notifies managers and requester, deduplicating if requester is a manager")
    void testSendStockReceiptConfirmed() {
        StockReceipt receipt = new StockReceipt();
        receipt.setStockReceiptId(10L);
        receipt.setReceiptNumber("REC-001");

        PurchaseOrder po = new PurchaseOrder();
        po.setOrderId(100L);
        po.setPoNumber("PO-100");
        po.setStatus(PurchaseOrderStatus.FULFILLED);
        po.setRequestedBy(managerUser1); // requester is also managerUser1!

        when(userRepository.findByRole_RoleNameAndStatus(RoleName.MANAGER, AccountStatus.ACTIVE))
                .thenReturn(List.of(managerUser1, managerUser2));

        notificationService.sendStockReceiptConfirmed(receipt, po);

        // Even though managerUser1 is in MANAGER role and also requestedBy, they should only get 1 notification!
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        List<Notification> notifs = captor.getAllValues();
        assertEquals(2, notifs.size());
        assertTrue(notifs.stream().allMatch(n -> n.getTitle().contains("REC-001")));
        assertTrue(notifs.stream().allMatch(n -> n.getMessage().contains("FULFILLED")));
    }

    // =========================================================================
    // 5. Stock Level Transitions (Downwards Only & Duplicate Suppression)
    // =========================================================================

    @Test
    @DisplayName("checkStockLevelTransition: NORMAL -> LOW_STOCK triggers LOW_STOCK alert")
    void testStockTransition_NormalToLowStock() {
        when(userRepository.findByRole_RoleNameAndStatus(RoleName.MANAGER, AccountStatus.ACTIVE))
                .thenReturn(List.of(managerUser1));
        when(userRepository.findByRole_RoleNameAndStatus(RoleName.WAREHOUSE_STAFF, AccountStatus.ACTIVE))
                .thenReturn(List.of(warehouseUser));

        Product product = new Product();
        product.setProductId(1L);
        product.setProductCode("PRD-001");
        product.setProductName("Organic Milk");
        product.setMinimumStockLevel(new BigDecimal("10.000"));

        // Previous: 15 (NORMAL), New: 8 (LOW_STOCK)
        notificationService.checkStockLevelTransition(product, new BigDecimal("15.000"), new BigDecimal("8.000"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        assertTrue(captor.getValue().getTitle().contains("Low Stock"));
        assertEquals(NotificationType.LOW_STOCK, captor.getValue().getType());
    }

    @Test
    @DisplayName("checkStockLevelTransition: LOW_STOCK -> OUT_OF_STOCK triggers OUT_OF_STOCK alert")
    void testStockTransition_LowStockToOutOfStock() {
        when(userRepository.findByRole_RoleNameAndStatus(RoleName.MANAGER, AccountStatus.ACTIVE))
                .thenReturn(List.of(managerUser1));
        when(userRepository.findByRole_RoleNameAndStatus(RoleName.WAREHOUSE_STAFF, AccountStatus.ACTIVE))
                .thenReturn(List.of(warehouseUser));

        Product product = new Product();
        product.setProductId(2L);
        product.setProductCode("PRD-002");
        product.setProductName("Bread");
        product.setMinimumStockLevel(new BigDecimal("10.000"));

        // Previous: 8 (LOW_STOCK), New: 0 (OUT_OF_STOCK)
        notificationService.checkStockLevelTransition(product, new BigDecimal("8.000"), BigDecimal.ZERO);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        assertTrue(captor.getValue().getTitle().contains("Out of Stock"));
        assertEquals(NotificationType.LOW_STOCK, captor.getValue().getType());
    }

    @Test
    @DisplayName("checkStockLevelTransition: NORMAL -> OUT_OF_STOCK triggers OUT_OF_STOCK alert")
    void testStockTransition_NormalToOutOfStock() {
        when(userRepository.findByRole_RoleNameAndStatus(RoleName.MANAGER, AccountStatus.ACTIVE))
                .thenReturn(List.of(managerUser1));
        when(userRepository.findByRole_RoleNameAndStatus(RoleName.WAREHOUSE_STAFF, AccountStatus.ACTIVE))
                .thenReturn(List.of(warehouseUser));

        Product product = new Product();
        product.setProductId(3L);
        product.setProductCode("PRD-003");
        product.setProductName("Butter");
        product.setMinimumStockLevel(new BigDecimal("5.000"));

        // Previous: 20 (NORMAL), New: 0 (OUT_OF_STOCK)
        notificationService.checkStockLevelTransition(product, new BigDecimal("20.000"), BigDecimal.ZERO);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        assertTrue(captor.getValue().getTitle().contains("Out of Stock"));
    }

    @Test
    @DisplayName("checkStockLevelTransition: LOW_STOCK -> LOW_STOCK suppresses duplicate alert")
    void testStockTransition_LowStockToLowStock_Suppressed() {
        Product product = new Product();
        product.setProductId(4L);
        product.setProductCode("PRD-004");
        product.setProductName("Detergent");
        product.setMinimumStockLevel(new BigDecimal("10.000"));

        // Previous: 8 (LOW_STOCK), New: 6 (LOW_STOCK) -> Stays in LOW_STOCK
        notificationService.checkStockLevelTransition(product, new BigDecimal("8.000"), new BigDecimal("6.000"));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkStockLevelTransition: OUT_OF_STOCK -> OUT_OF_STOCK suppresses duplicate alert")
    void testStockTransition_OutOfStockToOutOfStock_Suppressed() {
        Product product = new Product();
        product.setProductId(5L);
        product.setProductCode("PRD-005");
        product.setProductName("Soda");
        product.setMinimumStockLevel(new BigDecimal("10.000"));

        // Previous: 0, New: 0
        notificationService.checkStockLevelTransition(product, BigDecimal.ZERO, BigDecimal.ZERO);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkStockLevelTransition: Upward transition (e.g. LOW_STOCK -> NORMAL) does not alert")
    void testStockTransition_UpwardTransition_NoAlert() {
        Product product = new Product();
        product.setProductId(6L);
        product.setProductCode("PRD-006");
        product.setProductName("Soap");
        product.setMinimumStockLevel(new BigDecimal("10.000"));

        // Previous: 5 (LOW_STOCK), New: 25 (NORMAL after receipt)
        notificationService.checkStockLevelTransition(product, new BigDecimal("5.000"), new BigDecimal("25.000"));

        verify(notificationRepository, never()).save(any());
    }

    // =========================================================================
    // 6. Stock Expiry Daily Scheduled Check
    // =========================================================================

    @Test
    @DisplayName("runExpiryCheckNow sends consolidated alerts for expired and near-expiry batches")
    void testExpiryCheck_SendsConsolidatedAlerts() {
        when(userRepository.findByRole_RoleNameAndStatus(RoleName.MANAGER, AccountStatus.ACTIVE))
                .thenReturn(List.of(managerUser1));
        when(userRepository.findByRole_RoleNameAndStatus(RoleName.WAREHOUSE_STAFF, AccountStatus.ACTIVE))
                .thenReturn(List.of(warehouseUser));

        LocalDate today = LocalDate.now(StockExpiryNotifier.DEFAULT_ZONE);

        Product activeProduct = new Product();
        activeProduct.setProductId(10L);
        activeProduct.setProductCode("EXP-01");
        activeProduct.setProductName("Yogurt");
        activeProduct.setStatus(GeneralStatus.ACTIVE);
        activeProduct.setTrackExpiry(true);

        StockReceiptDetail expiredBatch = new StockReceiptDetail();
        expiredBatch.setStockReceiptItemId(101L);
        expiredBatch.setProduct(activeProduct);
        expiredBatch.setExpirationDate(today.minusDays(2)); // Expired

        StockReceiptDetail nearExpiryBatch = new StockReceiptDetail();
        nearExpiryBatch.setStockReceiptItemId(102L);
        nearExpiryBatch.setProduct(activeProduct);
        nearExpiryBatch.setExpirationDate(today.plusDays(10)); // Near expiry (< 30 days)

        when(stockReceiptDetailRepository.findActiveExpiringBatches())
                .thenReturn(List.of(expiredBatch, nearExpiryBatch));
        when(notificationRepository.existsRecentAlert(any(), any(), any())).thenReturn(false);

        stockExpiryNotifier.runExpiryCheckNow();

        // 2 alerts (expired + near-expiry) * 2 users (manager + warehouse) = 4 notifications saved
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(4)).save(captor.capture());

        List<Notification> saved = captor.getAllValues();
        long expiredAlertCount = saved.stream().filter(n -> n.getTitle().contains("Expired Batches")).count();
        long nearExpiryAlertCount = saved.stream().filter(n -> n.getTitle().contains("Near Expiry Batches")).count();

        assertEquals(2, expiredAlertCount);
        assertEquals(2, nearExpiryAlertCount);
    }

    @Test
    @DisplayName("runExpiryCheckNow deduplicates if alerts were already sent today")
    void testExpiryCheck_DeduplicatesWhenAlreadySentToday() {
        when(notificationRepository.existsRecentAlert(any(), any(), any())).thenReturn(true);

        stockExpiryNotifier.runExpiryCheckNow();

        verify(stockReceiptDetailRepository, never()).findActiveExpiringBatches();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("runExpiryCheckNow skips batches belonging to inactive products")
    void testExpiryCheck_SkipsInactiveProducts() {
        LocalDate today = LocalDate.now(StockExpiryNotifier.DEFAULT_ZONE);

        Product inactiveProduct = new Product();
        inactiveProduct.setProductId(20L);
        inactiveProduct.setStatus(GeneralStatus.INACTIVE);

        StockReceiptDetail expiredBatch = new StockReceiptDetail();
        expiredBatch.setStockReceiptItemId(201L);
        expiredBatch.setProduct(inactiveProduct);
        expiredBatch.setExpirationDate(today.minusDays(5));

        when(stockReceiptDetailRepository.findActiveExpiringBatches())
                .thenReturn(List.of(expiredBatch));
        when(notificationRepository.existsRecentAlert(any(), any(), any())).thenReturn(false);

        stockExpiryNotifier.runExpiryCheckNow();

        verify(notificationRepository, never()).save(any());
    }
}
