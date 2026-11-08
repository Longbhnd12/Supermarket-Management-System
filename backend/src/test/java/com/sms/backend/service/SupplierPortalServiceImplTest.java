package com.sms.backend.service;

import com.sms.backend.dto.supplierportal.RespondPurchaseOrderRequest;
import com.sms.backend.dto.supplierportal.RespondPurchaseOrderRequest.Action;
import com.sms.backend.dto.supplierportal.RespondPurchaseOrderRequest.ProposedItem;
import com.sms.backend.dto.supplierportal.SupplierOrderDetailResponse;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.PurchaseOrderDetail;
import com.sms.backend.entity.Role;
import com.sms.backend.entity.Supplier;
import com.sms.backend.entity.User;
import com.sms.backend.enums.NotificationType;
import com.sms.backend.enums.PurchaseOrderStatus;
import com.sms.backend.enums.RoleName;
import com.sms.backend.exception.AccountAccessException;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.PurchaseOrderDetailRepository;
import com.sms.backend.repository.SupplierPortalOrderRepository;
import com.sms.backend.security.CurrentUserProvider;
import com.sms.backend.service.NotificationService;
import com.sms.backend.service.serviceImpl.SupplierPortalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierPortalServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock SupplierPortalOrderRepository orders;
    @Mock PurchaseOrderDetailRepository details;
    @Mock NotificationService notifications;
    @Mock SystemLogService logs;
    @Mock CurrentUserProvider currentUser;

    SupplierPortalServiceImpl service;
    Supplier milk;
    User supplierUser;
    PurchaseOrder po;
    PurchaseOrderDetail line;

    @BeforeEach
    void setup() {
        service = new SupplierPortalServiceImpl(orders, details, notifications, logs, currentUser, CLOCK);

        milk = new Supplier();
        milk.setSupplierId(7L);
        milk.setSupplierName("Test Milk");

        Role supplierRole = new Role();
        supplierRole.setRoleName(RoleName.SUPPLIER);
        supplierUser = new User();
        supplierUser.setUserId(30L);
        supplierUser.setUsername("supplier01");
        supplierUser.setRole(supplierRole);
        supplierUser.setSupplier(milk);
        lenient().when(currentUser.currentUser()).thenReturn(Optional.of(supplierUser));

        User requester = new User();
        requester.setUserId(5L);
        po = new PurchaseOrder();
        po.setOrderId(100L);
        po.setPoNumber("PO-2026-0100");
        po.setSupplier(milk);
        po.setRequestedBy(requester);
        po.setStatus(PurchaseOrderStatus.SENT);
        po.setCreatedAt(LocalDateTime.of(2026, 11, 3, 9, 0));
        po.setExpectedDeliveryDate(LocalDate.of(2026, 11, 10));
        po.setTotalAmount(new BigDecimal("500000.00"));

        Product product = new Product();
        product.setProductId(1L);
        product.setProductName("Fresh milk 1L");
        line = new PurchaseOrderDetail();
        line.setOrderDetailId(1000L);
        line.setPurchaseOrder(po);
        line.setProduct(product);
        line.setRequestedQuantity(new BigDecimal("20"));
        line.setOrderedQuantity(new BigDecimal("20"));
        line.setUnitPrice(new BigDecimal("25000.00"));

        lenient().when(orders.findByIdForUpdate(100L)).thenReturn(Optional.of(po));
        lenient().when(orders.findLines(100L)).thenReturn(List.of(line));
    }

    private RespondPurchaseOrderRequest respond(Action action, String reason, LocalDate date, List<ProposedItem> items) {
        return new RespondPurchaseOrderRequest(action, reason, date, items);
    }

    @Test
    void acceptSetsStatusAcceptedLogsAndNotifiesStore() {
        SupplierOrderDetailResponse result = service.respond(100L, respond(Action.ACCEPT, null, null, null));

        assertEquals(PurchaseOrderStatus.ACCEPTED, po.getStatus());
        assertFalse(result.canRespond());
        assertEquals(LocalDateTime.now(CLOCK), po.getUpdatedAt());
        verify(logs).createLog(eq(supplierUser), eq("SUPPLIER_PO_ACCEPTED"), eq("PURCHASE_ORDER"), eq(100L), anyString(), any());
        verify(notifications).sendToUser(eq(po.getRequestedBy()), eq(NotificationType.PO_ALERT), anyString(), anyString());
        verify(notifications).sendToRole(eq(RoleName.MANAGER), eq(NotificationType.PO_ALERT), anyString(), anyString());
    }

    @Test
    void rejectNeedsAReason() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.respond(100L, respond(Action.REJECT, "  ", null, null)));
        assertEquals("The Reason / Comment field is required.", error.getMessage());
        assertEquals(PurchaseOrderStatus.SENT, po.getStatus());
        verify(orders, never()).save(any());
    }

    @Test
    void rejectWithReasonSetsRejectedBySupplier() {
        service.respond(100L, respond(Action.REJECT, "Out of stock this month", null, null));

        assertEquals(PurchaseOrderStatus.REJECTED_BY_SUPPLIER, po.getStatus());
        assertEquals("Out of stock this month", po.getResponseReason());
    }

    @Test
    void proposeChangesKeepsStatusSentAndStoresProposal() {
        SupplierOrderDetailResponse result = service.respond(100L, respond(Action.PROPOSE_CHANGES, "Only 15 left",
                LocalDate.of(2026, 11, 12), List.of(new ProposedItem(1000L, new BigDecimal("15"), null))));

        assertEquals(PurchaseOrderStatus.SENT, po.getStatus());
        assertEquals(0, new BigDecimal("15").compareTo(line.getProposedQuantity()));
        assertNull(line.getProposedUnitPrice());
        assertEquals(LocalDate.of(2026, 11, 12), po.getProposedDeliveryDate());
        assertEquals(0, new BigDecimal("375000").compareTo(result.proposedTotal())); // 15 x 25,000
        assertTrue(result.canRespond());
        verify(details).saveAll(List.of(line));
    }

    @Test
    void proposalWithoutAnyRealChangeIsRejected() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> service.respond(100L,
                respond(Action.PROPOSE_CHANGES, "same", LocalDate.of(2026, 11, 10),
                        List.of(new ProposedItem(1000L, new BigDecimal("20"), new BigDecimal("25000"))))));
        assertTrue(error.getMessage().startsWith("Enter at least one proposed change"));
    }

    @Test
    void proposedValuesMustBePositiveAndDateNotBeforeIssued() {
        assertThrows(BadRequestException.class, () -> service.respond(100L, respond(Action.PROPOSE_CHANGES, "x",
                null, List.of(new ProposedItem(1000L, BigDecimal.ZERO, null)))));
        assertThrows(BadRequestException.class, () -> service.respond(100L, respond(Action.PROPOSE_CHANGES, "x",
                LocalDate.of(2026, 11, 2), null)));
    }

    @Test
    void lineOfAnotherOrderIsRejected() {
        assertThrows(BadRequestException.class, () -> service.respond(100L, respond(Action.PROPOSE_CHANGES, "x",
                null, List.of(new ProposedItem(9999L, new BigDecimal("5"), null)))));
    }

    @Test
    void orderThatIsNoLongerSentCannotBeAnswered() {
        po.setStatus(PurchaseOrderStatus.ACCEPTED);

        assertThrows(BadRequestException.class, () -> service.respond(100L, respond(Action.REJECT, "late", null, null)));
    }

    @Test
    void orderOfAnotherSupplierIsNotFound() {
        Supplier other = new Supplier();
        other.setSupplierId(8L);
        po.setSupplier(other);

        assertThrows(ResourceNotFoundException.class, () -> service.respond(100L, respond(Action.ACCEPT, null, null, null)));
        when(orders.findById(100L)).thenReturn(Optional.of(po));
        assertThrows(ResourceNotFoundException.class, () -> service.getMyOrder(100L));
    }

    @Test
    void pendingApprovalOrderIsNotVisibleToSupplier() {
        po.setStatus(PurchaseOrderStatus.PENDING_APPROVAL);
        when(orders.findById(100L)).thenReturn(Optional.of(po));

        assertThrows(ResourceNotFoundException.class, () -> service.getMyOrder(100L));
    }

    @Test
    void accountWithoutSupplierLinkIsRefused() {
        supplierUser.setSupplier(null);

        assertThrows(AccountAccessException.class, () -> service.getMyOrder(100L));
    }
}
