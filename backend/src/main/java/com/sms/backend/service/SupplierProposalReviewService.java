package com.sms.backend.service;

import com.sms.backend.dto.supplierportal.ReviewSupplierProposalRequest;
import com.sms.backend.dto.supplierportal.SupplierProposalReviewResponse;

/** UC49 follow-up: internal Manager/Admin review of a supplier's proposed changes. */
public interface SupplierProposalReviewService {
    SupplierProposalReviewResponse review(Long orderId, ReviewSupplierProposalRequest request);
}
