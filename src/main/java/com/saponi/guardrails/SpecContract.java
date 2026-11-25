package com.saponi.guardrails;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Links a domain service, controller, or critical business method directly to a
 * versioned Spec-Driven Development (SDD) specification document.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface SpecContract {

    /**
     * Structured specification identifier (e.g. {@code SPEC-001}).
     */
    String specId();

    /**
     * Relative filename inside the specs directory (e.g. {@code SPEC-001-settlement-outbox.md}).
     * When omitted, {@link SpecTraceabilityVerifier} searches markdown files under {@code specsRoot}
     * for {@link #specId()}.
     */
    String specFile() default "";

    /**
     * Human-readable domain invariant enforced by the annotated target.
     */
    String invariant() default "";
}
