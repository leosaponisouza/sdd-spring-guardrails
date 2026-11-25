package com.saponi.guardrails.fixtures.violating;

import com.saponi.guardrails.SpecContract;
import org.springframework.stereotype.Service;

@Service
@SpecContract(specId = "SPEC-999", invariant = "Missing spec document")
public class UnbackedSpecService {

    @SpecContract(specId = "INVALID_ID")
    public void execute() {}
}
