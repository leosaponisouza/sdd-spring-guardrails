package com.saponi.guardrails;

import java.util.List;

/**
 * Raised when one or more {@link SpecContract} annotations violate SDD traceability rules.
 */
public class SpecViolationException extends AssertionError {

    private final List<String> violations;

    public SpecViolationException(List<String> violations) {
        super("SDD Spec Traceability violations (" + violations.size() + "):\n - "
                + String.join("\n - ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
