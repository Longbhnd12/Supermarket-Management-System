package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.sale.InvoiceResponse;
import com.sms.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class InvoiceController {
    private final SaleCompletionService completion;
    private final InvoicePdfService pdf;
    @GetMapping("/{id}/invoice")
    public ApiResponse<InvoiceResponse> invoice(@PathVariable Long id) {
        return ApiResponse.success("Invoice retrieved", completion.invoice(id));
    }
    @GetMapping("/{id}/invoice/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        InvoiceResponse invoice = completion.invoice(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(invoice.invoiceNumber() + ".pdf").build().toString())
            .cacheControl(CacheControl.noStore()).body(pdf.render(invoice));
    }
}
