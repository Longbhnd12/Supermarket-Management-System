// InvoiceSearchController - search and filter historical sales invoices
package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.response.InvoiceListItemResponse;
import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.service.InvoiceSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceSearchController {
    private final InvoiceSearchService service;
    @GetMapping
    public ApiResponse<PageResponse<InvoiceListItemResponse>> search(
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        try {
            return ApiResponse.success("Invoices retrieved", service.search(invoiceNumber,
                    date(fromDate), date(toDate), Integer.parseInt(page), Integer.parseInt(size)));
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new BadRequestException("Dates must use yyyy-MM-dd and page/size must be integers");
        }
    }
    private LocalDate date(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }
}
