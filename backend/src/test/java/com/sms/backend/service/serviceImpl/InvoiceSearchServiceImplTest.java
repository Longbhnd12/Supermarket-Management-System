package com.sms.backend.service.serviceImpl;

import com.sms.backend.entity.Sale;
import com.sms.backend.entity.User;
import com.sms.backend.enums.SaleStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.SaleRepository;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InvoiceSearchServiceImplTest {
    final SaleRepository sales = mock(SaleRepository.class);
    final InvoiceSearchServiceImpl service = new InvoiceSearchServiceImpl(sales);

    @Test void rejectsInvalidPagingDatesAndOversizedSearchBeforeQuery() {
        for (int[] paging : new int[][]{{-1, 20}, {0, 0}, {0, 101}})
            assertThrows(BadRequestException.class, () -> service.search(null, null, null, paging[0], paging[1]));
        assertThrows(BadRequestException.class, () -> service.search(null, LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 5), 0, 20));
        assertThrows(BadRequestException.class, () -> service.search("x".repeat(51), null, null, 0, 20));
        assertThrows(BadRequestException.class, () -> service.search(null, null, LocalDate.MAX, 0, 20));
        verifyNoInteractions(sales);
    }
    @Test void trimsNormalizesAndEscapesSqlServerLikeCharacters() {
        assertEquals("%inv-abc%", InvoiceSearchServiceImpl.containsPattern("  INV-AbC  "));
        assertEquals("%a\\%\\_\\[\\\\%", InvoiceSearchServiceImpl.containsPattern("A%_[\\"));
    }
    @Test @SuppressWarnings("unchecked")
    void combinesIssuedInvoiceAndDateCriteriaWithStableDatabasePaging() {
        Sale sale = new Sale(); User cashier = new User(); cashier.setUsername("cashier");
        sale.setSaleId(7L); sale.setInvoiceNumber("INV-7"); sale.setSaleNumber("SALE-7");
        sale.setCashier(cashier); sale.setTotalAmount(new BigDecimal("100.00"));
        when(sales.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(sale)));
        var day = LocalDate.of(2026, 11, 5);
        var result = service.search(" INV-7 ", day, day, 0, 20);
        assertEquals("INV-7", result.content().getFirst().invoiceNumber());
        assertEquals("cashier", result.content().getFirst().cashierUsername());
        ArgumentCaptor<Specification<Sale>> spec = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> paging = ArgumentCaptor.forClass(Pageable.class);
        verify(sales).findAll(spec.capture(), paging.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "completedAt", "saleId"), paging.getValue().getSort());
        Root<Sale> root = mock(Root.class); CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path status = mock(Path.class), number = mock(Path.class), snapshot = mock(Path.class), completed = mock(Path.class);
        when(root.get("status")).thenReturn(status); when(root.get("invoiceNumber")).thenReturn(number);
        when(root.get("invoiceSnapshot")).thenReturn(snapshot); when(root.get("completedAt")).thenReturn(completed);
        when(query.getResultType()).thenReturn(Sale.class); when(cb.lower(number)).thenReturn(number); when(cb.trim(number)).thenReturn(number);
        spec.getValue().toPredicate(root, query, cb);
        verify(cb).equal(status, SaleStatus.COMPLETED); verify(cb).isNotNull(snapshot);
        verify(cb).notEqual(number, ""); verify(cb).like(number, "%inv-7%", '\\');
        verify(cb).greaterThanOrEqualTo(completed, day.atStartOfDay());
        verify(cb).lessThan(completed, day.plusDays(1).atStartOfDay());
        verify(root).fetch("cashier", JoinType.LEFT);
        verify(cb).and(any(Predicate[].class));
        clearInvocations(root); when(query.getResultType()).thenReturn(Long.class);
        spec.getValue().toPredicate(root, query, cb);
        verify(root, never()).fetch(anyString(), any(JoinType.class));
    }
    @Test @SuppressWarnings("unchecked") void emptyResultsAreSuccessful() {
        when(sales.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertTrue(service.search(null, null, null, 0, 20).content().isEmpty());
    }
}
