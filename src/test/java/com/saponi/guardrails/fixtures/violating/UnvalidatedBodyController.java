package com.saponi.guardrails.fixtures.violating;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UnvalidatedBodyController {

    @PostMapping("/orders")
    public String create(@RequestBody CreateOrderInput input) {
        return input.sku();
    }

    public record CreateOrderInput(String sku) {}
}
