package com.saponi.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saponi.guardrails.fixtures.compliant.CompliantInvoiceController;
import com.saponi.guardrails.fixtures.violating.DirectRepoController;
import com.saponi.guardrails.fixtures.violating.EntityLeakController;
import com.saponi.guardrails.fixtures.violating.LocalDateTimeController;
import com.saponi.guardrails.fixtures.violating.UnbackedSpecService;
import com.saponi.guardrails.fixtures.violating.UnvalidatedBodyController;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SddArchitectureRulesTest {

    @TempDir
    Path tempSpecsDir;

    @Test
    void compliantModuleShouldPassAllArchitectureAndSpecTraceabilityRules() throws Exception {
        Files.writeString(
                tempSpecsDir.resolve("SPEC-001-invoice-issuance.md"),
                "# SPEC-001: Invoice Issuance\n\nAll timestamps use OffsetDateTime and @Valid DTOs.\n"
        );

        JavaClasses compliantClasses = new ClassFileImporter()
                .importPackagesOf(CompliantInvoiceController.class);

        assertThatCode(() -> {
            SddArchitectureRules.enforceArchitecture(compliantClasses);
            SpecTraceabilityVerifier.verify(compliantClasses, tempSpecsDir);
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldDetectControllerDirectlyAccessingRepository() {
        JavaClasses classes = new ClassFileImporter().importClasses(
                DirectRepoController.class,
                DirectRepoController.InvoiceRepository.class
        );

        assertThatThrownBy(() ->
                SddArchitectureRules.controllersMustNotAccessRepositoriesDirectly().check(classes)
        ).isInstanceOf(AssertionError.class)
                .hasMessageContaining("InvoiceRepository");
    }

    @Test
    void shouldDetectJpaEntityExposedInsideGenericResponseEntity() {
        JavaClasses classes = new ClassFileImporter().importClasses(
                EntityLeakController.class,
                EntityLeakController.InvoiceEntity.class
        );

        assertThatThrownBy(() ->
                SddArchitectureRules.controllersMustNotExposeJpaEntities().check(classes)
        ).isInstanceOf(AssertionError.class)
                .hasMessageContaining("exposes @Entity class")
                .hasMessageContaining("InvoiceEntity");
    }

    @Test
    void shouldDetectAmbiguousLocalDateTimeInsideNestedApiRecord() {
        JavaClasses classes = new ClassFileImporter().importClasses(
                LocalDateTimeController.class,
                LocalDateTimeController.EventPayload.class
        );

        assertThatThrownBy(() ->
                SddArchitectureRules.apiPayloadsMustNotUseAmbiguousLocalDateTime().check(classes)
        ).isInstanceOf(AssertionError.class)
                .hasMessageContaining("LocalDateTime");
    }

    @Test
    void shouldDetectUnvalidatedRequestBodyOnMutatingEndpoint() {
        JavaClasses classes = new ClassFileImporter().importClasses(
                UnvalidatedBodyController.class,
                UnvalidatedBodyController.CreateOrderInput.class
        );

        assertThatThrownBy(() ->
                SddArchitectureRules.mutatingEndpointsMustValidateRequestBody().check(classes)
        ).isInstanceOf(AssertionError.class)
                .hasMessageContaining("without @Valid or @Validated");
    }

    @Test
    void shouldReportUnbackedAndMalformedSpecContracts() throws Exception {
        Files.writeString(tempSpecsDir.resolve("SPEC-001.md"), "# SPEC-001\n");

        JavaClasses classes = new ClassFileImporter().importClasses(UnbackedSpecService.class);

        assertThatThrownBy(() -> SpecTraceabilityVerifier.verify(classes, tempSpecsDir))
                .isInstanceOf(SpecViolationException.class)
                .satisfies(err -> {
                    SpecViolationException ex = (SpecViolationException) err;
                    assertThat(ex.getViolations()).hasSize(2);
                    assertThat(ex.getMessage()).contains("SPEC-999");
                    assertThat(ex.getMessage()).contains("INVALID_ID");
                });
    }
}
