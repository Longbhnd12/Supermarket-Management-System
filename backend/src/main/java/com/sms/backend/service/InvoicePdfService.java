package com.sms.backend.service;

import com.sms.backend.dto.sale.InvoiceResponse;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;
import java.io.*;
import java.text.Normalizer;
import java.util.*;

@Service
public class InvoicePdfService {
    /** Rendering has no write transaction and cannot undo an issued invoice. */
    public byte[] render(InvoiceResponse invoice) {
        try (PDDocument document = new PDDocument();
             InputStream fontFile = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (fontFile == null) throw new IOException("Invoice font is missing");
            PDType0Font font = PDType0Font.load(document, fontFile);
            List<String> text = new ArrayList<>();
            text.add(invoice.storeName()); text.add("HÓA ĐƠN BÁN HÀNG");
            text.add("Số hóa đơn: " + invoice.invoiceNumber());
            text.add("Đơn bán: " + invoice.saleNumber());
            text.add("Ngày hoàn tất: " + invoice.completedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            text.add("Thu ngân: " + invoice.cashier());
            text.add("Thanh toán: " + invoice.paymentMethod()); text.add("");
            for (var item : invoice.items()) {
                text.add(item.productCode() + " | " + item.productName());
                text.add("  " + item.quantity().toPlainString() + " " + item.unit() + " x " + money(item.unitPrice())
                    + " | Thành tiền: " + money(item.subtotal().subtract(item.discountAmount())));
                if (item.discountAmount().signum() > 0) text.add("  Giảm giá: " + money(item.discountAmount()));
                text.add("");
            }
            text.add("Tiền hàng: " + money(invoice.subtotal())); text.add("Giảm giá: " + money(invoice.discountAmount()));
            text.add("TỔNG THANH TOÁN: " + money(invoice.totalAmount()));
            if (invoice.receivedAmount() != null) {
                text.add("Khách đưa: " + money(invoice.receivedAmount())); text.add("Tiền trả lại: " + money(invoice.changeAmount()));
            }
            text.add(""); text.add("Chứng từ bán hàng nội bộ. Không phải hóa đơn thuế GTGT.");
            List<String> wrapped = new ArrayList<>();
            for (String line : text) {
                String normalized = Normalizer.normalize(line, Normalizer.Form.NFC).replaceAll("[\\p{Cc}\\p{Cf}]", " ");
                StringBuilder part = new StringBuilder();
                for (int cp : normalized.codePoints().toArray()) {
                    String ch = new String(Character.toChars(cp));
                    try { font.encode(ch); } catch (IllegalArgumentException e) { ch = "?"; }
                    if (font.getStringWidth(part + ch) / 1000 * 11 > 495) { wrapped.add(part.toString()); part.setLength(0); }
                    part.append(ch);
                }
                wrapped.add(part.toString());
            }
            for (int offset = 0; offset < wrapped.size(); offset += 43) {
                PDPage page = new PDPage(PDRectangle.A4); document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText(); content.setFont(font, 11); content.setLeading(17); content.newLineAtOffset(50, 790);
                    for (String line : wrapped.subList(offset, Math.min(offset + 43, wrapped.size()))) { content.showText(line); content.newLine(); }
                    content.endText();
                }
            }
            document.save(output); return output.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Could not render invoice PDF; invoice remains issued", e); }
    }
    private String money(java.math.BigDecimal amount) {
        return java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " VND";
    }
}
