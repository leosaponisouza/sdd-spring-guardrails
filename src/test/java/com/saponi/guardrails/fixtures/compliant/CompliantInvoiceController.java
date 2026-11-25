package com.saponi.guardrails.fixtures.compliant;

import com.saponi.guardrails.SpecContract;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
@SpecContract(
        specId = "SPEC-001",
        specFile = "SPEC-001-invoice-issuance.md",
        invariant = "Invoices expose immutable DTOs with OffsetDateTime and Bean Validation"
)
public class CompliantInvoiceController {

    private final CompliantInvoiceService service;

    public CompliantInvoiceController(CompliantInvoiceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<InvoiceView> issue(@Valid @RequestBody IssueInvoiceInput input) {
        return ResponseEntity.ok(service.issue(input));
    }

    public record IssueInvoiceInput(@NotBlank String customerId, BigDecimal amount, OffsetDateTime dueAt) {}

    public record InvoiceView(String id, BigDecimal amount, OffsetDateTime issuedAt) {}

    @Service
    public static class CompliantInvoiceService {
        public InvoiceView issue(IssueInvoiceInput input) {
            return new InvoiceView("INV-1", input.amount(), OffsetDateTime.now());
        }
    }
}
