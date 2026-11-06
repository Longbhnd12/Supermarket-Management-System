package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.response.InvoiceListItemResponse;
import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.entity.Sale;
import com.sms.backend.enums.SaleStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.SaleRepository;
import com.sms.backend.service.InvoiceSearchService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceSearchServiceImpl implements InvoiceSearchService {
    private final SaleRepository sales;

    @Override
    public PageResponse<InvoiceListItemResponse> search(String invoiceNumber, LocalDate fromDate, LocalDate toDate, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("Page must be non-negative and size must be between 1 and 100");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) throw new BadRequestException("From date cannot be after To date");
        if (toDate != null && toDate.equals(LocalDate.MAX)) throw new BadRequestException("To date is out of range");
        if (invoiceNumber != null && invoiceNumber.trim().length() > 50) throw new BadRequestException("Invoice number must not exceed 50 characters");
        Specification<Sale> filter = (root, query, cb) -> {
            // Fetch only on the content query; the count query must remain scalar.
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) root.fetch("cashier", JoinType.LEFT);
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("status"), SaleStatus.COMPLETED));
            predicates.add(cb.isNotNull(root.get("invoiceNumber")));
            predicates.add(cb.notEqual(cb.trim(root.get("invoiceNumber")), ""));
            predicates.add(cb.isNotNull(root.get("invoiceSnapshot")));
            if (invoiceNumber != null && !invoiceNumber.isBlank()) predicates.add(cb.like(cb.lower(root.get("invoiceNumber")), containsPattern(invoiceNumber), '\\'));
            // UC30 stores Vietnam-local LocalDateTime; apply Vietnam calendar boundaries.
            if (fromDate != null) predicates.add(cb.greaterThanOrEqualTo(root.get("completedAt"), fromDate.atStartOfDay()));
            if (toDate != null) predicates.add(cb.lessThan(root.get("completedAt"), toDate.plusDays(1).atStartOfDay()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var paging = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "completedAt", "saleId"));
        return PageResponse.from(sales.findAll(filter, paging).map(s -> new InvoiceListItemResponse(
                s.getSaleId(), s.getInvoiceNumber(), s.getSaleNumber(), s.getCompletedAt(),
                s.getCashier() == null ? null : s.getCashier().getUsername(), s.getTotalAmount())));
    }

    static String containsPattern(String value) {
        // SQL Server also treats '[' as a LIKE metacharacter.
        return "%" + value.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_").replace("[", "\\[") + "%";
    }
}
