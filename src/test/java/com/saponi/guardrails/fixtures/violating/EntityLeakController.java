package com.saponi.guardrails.fixtures.violating;

import jakarta.persistence.Entity;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EntityLeakController {

    @GetMapping("/entities")
    public ResponseEntity<List<InvoiceEntity>> listEntities() {
        return ResponseEntity.ok(List.of());
    }

    @Entity
    public static class InvoiceEntity {}
}
