package com.sms.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueByDayResponse(LocalDate date, BigDecimal revenue) {}
