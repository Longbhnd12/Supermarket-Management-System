package com.sms.backend.service;

import com.sms.backend.entity.Product;
import com.sms.backend.entity.SaleItem;
import com.sms.backend.entity.StockReceiptDetail;
import java.math.BigDecimal;
import java.util.List;

/** Shared FEFO stock validation and eligibility for payment and completion. */
public interface FefoStockService {
    void validateBeforePayment(List<SaleItem> lines);

    List<StockReceiptDetail> lockEligibleBatches(Product product);

    BigDecimal available(Product product, List<StockReceiptDetail> eligible);

    BigDecimal getAvailableSellableQuantity(Product product);
}
