// InvoicePdfService - service interface for generating printable invoice documents
package com.sms.backend.service;

import com.sms.backend.dto.sale.InvoiceResponse;

/** Renders an issued invoice without changing sale or payment state. */
public interface InvoicePdfService {
    byte[] render(InvoiceResponse invoice);
}
