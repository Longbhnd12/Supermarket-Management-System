package com.sms.backend.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

/** VNPay protocol response, deliberately not ApiResponse. */
public record IpnResponse(@JsonProperty("RspCode") String rspCode, @JsonProperty("Message") String message) {}
