package com.sms.backend.service;

import com.sms.backend.dto.sale.InvoiceResponse;

/** Completes paid sales and reads their immutable invoice snapshots. */
public interface SaleCompletionService {
    InvoiceResponse complete(Long id);

    InvoiceResponse invoice(Long id);
}
