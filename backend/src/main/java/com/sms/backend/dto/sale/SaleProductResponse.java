package com.sms.backend.dto.sale;

import java.math.BigDecimal;

public record SaleProductResponse(Long productId, String productCode, String barcode,
                                  String productName, String unit, BigDecimal sellingPrice,
                                  BigDecimal stockQuantity) {}
