package com.saponi.guardrails.fixtures.violating;

import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DirectRepoController {

    private final InvoiceRepository repository;

    public DirectRepoController(InvoiceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/leak-repo")
    public String count() {
        return repository.toString();
    }

    @Repository
    public interface InvoiceRepository {}
}
