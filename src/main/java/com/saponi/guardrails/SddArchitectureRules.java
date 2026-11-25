package com.saponi.guardrails;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.validation.Valid;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reusable ArchUnit + SDD architectural guardrails designed to keep AI-assisted and human-authored
 * Spring Boot services aligned with strict boundary, contract, and timestamp invariants.
 */
public final class SddArchitectureRules {

    private SddArchitectureRules() {}

    /**
     * Runs the complete guardrail suite (layered boundaries, entity encapsulation, explicit timezone
     * timestamps, {@code @RequestBody} validation, and {@link SpecContract} Markdown traceability).
     */
    public static void enforceAll(String basePackage, Path specsDirectory) {
        JavaClasses classes = importProductionClasses(basePackage);
        enforceArchitecture(classes);
        SpecTraceabilityVerifier.verify(classes, specsDirectory);
    }

    /**
     * Runs all bytecode architecture rules without requiring a {@code docs/specs} directory.
     */
    public static void enforceArchitecture(JavaClasses classes) {
        controllersMustNotAccessRepositoriesDirectly().allowEmptyShould(true).check(classes);
        servicesMustNotDependOnWebControllers().allowEmptyShould(true).check(classes);
        controllersMustNotExposeJpaEntities().allowEmptyShould(true).check(classes);
        apiPayloadsMustNotUseAmbiguousLocalDateTime().allowEmptyShould(true).check(classes);
        mutatingEndpointsMustValidateRequestBody().allowEmptyShould(true).check(classes);
    }

    public static JavaClasses importProductionClasses(String basePackage) {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
    }

    /**
     * Rule 1: {@code @RestController} classes must delegate through application/domain services rather
     * than injecting {@code @Repository} or Spring Data {@code *Repository} interfaces directly.
     */
    public static ArchRule controllersMustNotAccessRepositoriesDirectly() {
        return noClasses()
                .that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat()
                .areAnnotatedWith(Repository.class)
                .orShould().dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")
                .because("REST controllers must delegate persistence orchestration to @Service / use-case components");
    }

    /**
     * Rule 2: {@code @Service} components must remain transport-agnostic and never depend on
     * {@code @RestController} classes.
     */
    public static ArchRule servicesMustNotDependOnWebControllers() {
        return noClasses()
                .that().areAnnotatedWith(Service.class)
                .should().dependOnClassesThat()
                .areAnnotatedWith(RestController.class)
                .because("Domain and application services must not depend on HTTP controllers");
    }

    /**
     * Rule 3: HTTP endpoints must never accept or return {@code @Entity} types directly or wrapped inside
     * generic containers such as {@code ResponseEntity<List<Entity>>}.
     */
    public static ArchRule controllersMustNotExposeJpaEntities() {
        return methods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and().arePublic()
                .should(new ArchCondition<>("not expose @Entity types in HTTP signatures") {
                    @Override
                    public void check(JavaMethod item, ConditionEvents events) {
                        Method reflected = item.reflect();
                        if (!isEndpointMethod(reflected)) {
                            return;
                        }
                        Set<Class<?>> inspected = new HashSet<>();
                        inspectTypeForEntity(reflected.getGenericReturnType(), item, events, inspected);
                        for (Type paramType : reflected.getGenericParameterTypes()) {
                            inspectTypeForEntity(paramType, item, events, inspected);
                        }
                    }
                })
                .because("Exposing JPA @Entity classes at the HTTP boundary leaks persistence state and enables mass-assignment vulnerabilities");
    }

    /**
     * Rule 4: API request/response DTOs must use timezone-explicit instants ({@code OffsetDateTime} or
     * {@code Instant}) instead of ambiguous {@code LocalDateTime}.
     */
    public static ArchRule apiPayloadsMustNotUseAmbiguousLocalDateTime() {
        return methods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and().arePublic()
                .should(new ArchCondition<>("use OffsetDateTime/Instant rather than LocalDateTime in API contracts") {
                    @Override
                    public void check(JavaMethod item, ConditionEvents events) {
                        Method reflected = item.reflect();
                        if (!isEndpointMethod(reflected)) {
                            return;
                        }
                        Set<Class<?>> visited = new HashSet<>();
                        inspectTypeForLocalDateTime(reflected.getGenericReturnType(), item, events, visited, 0);
                        for (Type paramType : reflected.getGenericParameterTypes()) {
                            inspectTypeForLocalDateTime(paramType, item, events, visited, 0);
                        }
                    }
                })
                .because("Ambiguous LocalDateTime fields cause silent UTC vs. local timezone drift across distributed clients");
    }

    /**
     * Rule 5: Mutating HTTP endpoints ({@code POST}, {@code PUT}, {@code PATCH}) accepting a
     * {@code @RequestBody} must trigger Bean Validation via {@code @Valid} or {@code @Validated}.
     */
    public static ArchRule mutatingEndpointsMustValidateRequestBody() {
        return methods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and().arePublic()
                .should(new ArchCondition<>("validate @RequestBody parameters with @Valid or @Validated") {
                    @Override
                    public void check(JavaMethod item, ConditionEvents events) {
                        Method reflected = item.reflect();
                        if (!isMutatingEndpoint(reflected)) {
                            return;
                        }
                        for (Parameter parameter : reflected.getParameters()) {
                            if (parameter.isAnnotationPresent(RequestBody.class)) {
                                boolean validated = parameter.isAnnotationPresent(Valid.class)
                                        || parameter.isAnnotationPresent(Validated.class);
                                if (!validated) {
                                    events.add(SimpleConditionEvent.violated(
                                            item,
                                            item.getFullName() + " accepts @RequestBody '"
                                                    + parameter.getName() + "' without @Valid or @Validated"
                                    ));
                                }
                            }
                        }
                    }
                })
                .because("Every mutating @RequestBody payload must enforce Bean Validation invariants at the HTTP boundary");
    }

    private static boolean isEndpointMethod(Method method) {
        return method.isAnnotationPresent(RequestMapping.class)
                || method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class);
    }

    private static boolean isMutatingEndpoint(Method method) {
        return method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(PatchMapping.class);
    }

    private static void inspectTypeForEntity(
            Type type,
            JavaMethod origin,
            ConditionEvents events,
            Set<Class<?>> visited
    ) {
        if (type instanceof Class<?> raw) {
            if (!visited.add(raw)) {
                return;
            }
            if (raw.isAnnotationPresent(Entity.class)) {
                events.add(SimpleConditionEvent.violated(
                        origin,
                        origin.getFullName() + " exposes @Entity class " + raw.getName()
                ));
            }
        } else if (type instanceof ParameterizedType parameterized) {
            inspectTypeForEntity(parameterized.getRawType(), origin, events, visited);
            for (Type arg : parameterized.getActualTypeArguments()) {
                inspectTypeForEntity(arg, origin, events, visited);
            }
        }
    }

    private static void inspectTypeForLocalDateTime(
            Type type,
            JavaMethod origin,
            ConditionEvents events,
            Set<Class<?>> visited,
            int depth
    ) {
        if (depth > 4 || type == null) {
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            inspectTypeForLocalDateTime(parameterized.getRawType(), origin, events, visited, depth + 1);
            for (Type arg : parameterized.getActualTypeArguments()) {
                inspectTypeForLocalDateTime(arg, origin, events, visited, depth + 1);
            }
            return;
        }
        if (!(type instanceof Class<?> raw) || raw.isPrimitive() || raw.isEnum()) {
            return;
        }
        if (raw == LocalDateTime.class) {
            events.add(SimpleConditionEvent.violated(
                    origin,
                    origin.getFullName() + " exposes java.time.LocalDateTime in HTTP contract"
            ));
            return;
        }
        if (raw.getPackageName().startsWith("java.")
                || raw.getPackageName().startsWith("jakarta.")
                || raw.getPackageName().startsWith("org.springframework.")
                || !visited.add(raw)) {
            return;
        }
        if (raw.isRecord()) {
            for (RecordComponent rc : raw.getRecordComponents()) {
                inspectTypeForLocalDateTime(rc.getGenericType(), origin, events, visited, depth + 1);
            }
        } else {
            for (Field field : raw.getDeclaredFields()) {
                inspectTypeForLocalDateTime(field.getGenericType(), origin, events, visited, depth + 1);
            }
        }
    }
}
