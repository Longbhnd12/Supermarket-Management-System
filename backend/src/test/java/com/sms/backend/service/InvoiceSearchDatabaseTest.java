package com.sms.backend.service;

import com.sms.backend.entity.*;
import com.sms.backend.enums.SaleStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** SQL Server query verification. All fixtures roll back with the test transaction. */
@SpringBootTest(properties = {"sale.completion-delay-ms=600000", "payment.reconcile-delay-ms=600000"})
@Transactional
class InvoiceSearchDatabaseTest {
    @Autowired EntityManager em;
    @Autowired InvoiceSearchService search;

    @Test void issuedOnlyLiteralSearchInclusiveDatesAndStablePaging() {
        String prefix = "UC22-" + UUID.randomUUID();
        Role role = em.createQuery("select r from Role r", Role.class).setMaxResults(1).getSingleResult();
        User cashier = new User(); cashier.setRole(role); cashier.setUsername(prefix);
        cashier.setEmail(prefix + "@test.invalid"); cashier.setPasswordHash("test-only"); em.persist(cashier);
        LocalDate day = LocalDate.of(2090, 6, 15);
        Sale first = sale(cashier, prefix + "%_[", SaleStatus.COMPLETED, "{}", day.atStartOfDay());
        Sale second = sale(cashier, prefix + "B", SaleStatus.COMPLETED, "{}", day.atTime(23, 59, 59));
        Sale third = sale(cashier, prefix + "C", SaleStatus.COMPLETED, "{}", day.atTime(23, 59, 59));
        sale(cashier, prefix + "D", SaleStatus.COMPLETED, "{}", day.plusDays(1).atStartOfDay());
        sale(cashier, prefix + "E", SaleStatus.DRAFT, null, day.atStartOfDay());
        sale(cashier, prefix + "F", SaleStatus.CANCELLED, "{}", day.atStartOfDay());
        sale(cashier, prefix + "G", SaleStatus.COMPLETED, null, day.atStartOfDay());
        sale(cashier, " ", SaleStatus.COMPLETED, "{}", day.atStartOfDay());
        em.flush(); em.clear();

        var literal = search.search("  " + (prefix + "%_[").toLowerCase(java.util.Locale.ROOT) + "  ", day, day, 0, 20);
        assertEquals(1, literal.totalElements());
        assertEquals(first.getSaleId(), literal.content().getFirst().saleId());
        var page = search.search(prefix, day, day, 0, 2);
        assertEquals(3, page.totalElements()); assertEquals(2, page.totalPages());
        assertFalse(page.last());
        assertEquals(third.getSaleId(), page.content().get(0).saleId());
        assertEquals(second.getSaleId(), page.content().get(1).saleId());
        var last = search.search(prefix, day, day, 1, 2);
        assertTrue(last.last()); assertEquals(first.getSaleId(), last.content().getFirst().saleId());
        assertEquals(4, search.search(prefix, null, null, 0, 20).totalElements());
        assertEquals(3, search.search(prefix, null, day, 0, 20).totalElements());
        assertEquals(1, search.search(prefix, day.plusDays(1), null, 0, 20).totalElements());
        assertTrue(search.search(prefix + "-missing", day, day, 0, 20).content().isEmpty());
        assertEquals("{}", em.find(Sale.class, first.getSaleId()).getInvoiceSnapshot());
        assertEquals(SaleStatus.COMPLETED, em.find(Sale.class, first.getSaleId()).getStatus());
    }

    private Sale sale(User cashier, String invoice, SaleStatus status, String snapshot, LocalDateTime completedAt) {
        Sale sale = new Sale(); sale.setCashier(cashier); sale.setSaleNumber("SALE-" + UUID.randomUUID());
        sale.setInvoiceNumber(invoice); sale.setStatus(status); sale.setInvoiceSnapshot(snapshot);
        sale.setCreatedAt(completedAt.minusDays(1)); sale.setCompletedAt(completedAt);
        sale.setSubtotal(new BigDecimal("100.00")); sale.setTotalAmount(new BigDecimal("100.00"));
        em.persist(sale); return sale;
    }
}
