# sdd-spring-guardrails

[![CI](https://github.com/leosaponisouza/sdd-spring-guardrails/actions/workflows/ci.yml/badge.svg)](https://github.com/leosaponisouza/sdd-spring-guardrails/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![ArchUnit 1.3](https://img.shields.io/badge/ArchUnit-1.3.0-5C2D91?style=flat-square)
![Spring 6](https://img.shields.io/badge/Spring%20Web-6.x-6DB33F?style=flat-square&logo=spring&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)

Executable **Spec-Driven Development (SDD)** and **ArchUnit** architectural guardrails for **Java 21 & Spring Boot** microservices.

When engineering teams combine human developers and AI coding agents (Claude Code, Copilot, MCP workflows), prose guidelines in `CLAUDE.md` or ADRs inevitably drift unless enforced at compile/test time. `sdd-spring-guardrails` turns architectural boundaries, API contract hygiene, and Markdown specification traceability into deterministic JUnit 5 assertions that fail CI in milliseconds.

---

## Enforced Invariants

| Rule | Failure Prevented in Production |
|---|---|
| **`controllersMustNotAccessRepositoriesDirectly`** | Prevents `@RestController` classes from bypassing `@Service` / transactional use-case boundaries by injecting `@Repository` or `*Repository` interfaces directly. |
| **`servicesMustNotDependOnWebControllers`** | Keeps `@Service` domain/application layers transport-agnostic and free of HTTP controller dependencies. |
| **`controllersMustNotExposeJpaEntities`** | Recursively inspects generic return and parameter types (e.g., `ResponseEntity<List<OrderEntity>>`) to block JPA `@Entity` leakage, `LazyInitializationException`, and mass-assignment vulnerabilities. |
| **`apiPayloadsMustNotUseAmbiguousLocalDateTime`** | Recursively walks API DTOs and Java records to forbid timezone-ambiguous `java.time.LocalDateTime`, requiring explicit `OffsetDateTime` or `Instant` contracts. |
| **`mutatingEndpointsMustValidateRequestBody`** | Ensures every `@PostMapping` / `@PutMapping` / `@PatchMapping` `@RequestBody` parameter carries `@Valid` or `@Validated`. |
| **`@SpecContract` + `SpecTraceabilityVerifier`** | Verifies at test time that every `@SpecContract(specId = "SPEC-001")` annotation in compiled bytecode maps to a real versioned Markdown specification under `docs/specs/`. |

---

## Quick Start

### 1. Add Dependency (JitPack / Maven)

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.leosaponisouza</groupId>
    <artifactId>sdd-spring-guardrails</artifactId>
    <version>1.0.0</version>
</dependency>
```

*(Keep `@SpecContract` in `compile` scope if annotating production services, or run `SddArchitectureRules.enforceArchitecture(...)` purely in `test` scope.)*

### 2. Annotate Critical Use Cases with `@SpecContract`

```java
@Service
@SpecContract(
    specId = "SPEC-001",
    specFile = "SPEC-001-settlement-and-outbox.md",
    invariant = "Duplicate Pix webhooks never settle a transfer more than once"
)
public class PixWebhookService {
    // ...
}
```

### 3. Enforce Everything in 3 Lines of JUnit 5

```java
class ArchitectureGuardrailsTest {

    @Test
    void serviceShouldRespectAllSddGuardrails() {
        SddArchitectureRules.enforceAll(
            "com.rephelper.settlement",
            Path.of("docs/specs")
        );
    }
}
```

---

## Used In Production-Style Showcase Services

- [**rep-helper-settlement-engine**](https://github.com/leosaponisouza/rep-helper-settlement-engine) — Min-Cash-Flow debt simplification, idempotent Pix webhooks & Transactional Outbox.
- [**exomadb-engine**](https://github.com/leosaponisouza/exomadb-engine) — High-throughput VCF genomic variant ingestion & PostgreSQL keyset pagination engine.
- [**rep-helper**](https://github.com/leosaponisouza/rep-helper) — Full-stack shared housing management monorepo.

---

## Local Build & Verification

```bash
mvn -B verify
```

---

## License

Released under the [MIT License](LICENSE).
