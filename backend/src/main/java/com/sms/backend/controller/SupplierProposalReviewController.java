package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.supplierportal.ReviewSupplierProposalRequest;
import com.sms.backend.dto.supplierportal.SupplierProposalReviewResponse;
import com.sms.backend.service.SupplierProposalReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal follow-up for UC49 supplier proposals; Manager/Admin only (also enforced by the service). */
@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class SupplierProposalReviewController {

    private final SupplierProposalReviewService supplierProposalReviewService;

    @PostMapping("/{id}/supplier-proposal/review")
    public ResponseEntity<ApiResponse<SupplierProposalReviewResponse>> review(
            @PathVariable Long id,
            @Valid @RequestBody ReviewSupplierProposalRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Supplier proposal reviewed", supplierProposalReviewService.review(id, request)));
    }
}
