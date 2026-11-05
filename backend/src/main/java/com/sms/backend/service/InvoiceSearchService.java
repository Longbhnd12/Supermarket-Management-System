package com.sms.backend.service;

import com.sms.backend.dto.response.InvoiceListItemResponse;
import com.sms.backend.dto.response.PageResponse;
import java.time.LocalDate;

public interface InvoiceSearchService {
    PageResponse<InvoiceListItemResponse> search(String invoiceNumber, LocalDate fromDate, LocalDate toDate, int page, int size);
}
