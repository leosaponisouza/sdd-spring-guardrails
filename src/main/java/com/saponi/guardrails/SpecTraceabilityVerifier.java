package com.saponi.guardrails;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Verifies that every {@link SpecContract} in compiled bytecode maps to an existing
 * Markdown specification containing the declared {@code specId}.
 */
public final class SpecTraceabilityVerifier {

    private static final Pattern SPEC_ID_PATTERN = Pattern.compile("^SPEC-\\d{3,}(-[A-Za-z0-9_-]+)?$");

    private SpecTraceabilityVerifier() {}

    public static void verify(JavaClasses classes, Path specsRoot) {
        List<String> violations = collectViolations(classes, specsRoot);
        if (!violations.isEmpty()) {
            throw new SpecViolationException(violations);
        }
    }

    public static List<String> collectViolations(JavaClasses classes, Path specsRoot) {
        List<String> violations = new ArrayList<>();
        if (specsRoot == null || !Files.isDirectory(specsRoot)) {
            violations.add("Specs directory does not exist: " + specsRoot);
            return violations;
        }

        List<Path> markdownFiles = listMarkdownFiles(specsRoot, violations);

        for (JavaClass javaClass : classes) {
            if (javaClass.isAnnotatedWith(SpecContract.class)) {
                SpecContract contract = javaClass.getAnnotationOfType(SpecContract.class);
                validateContract(javaClass.getName(), contract, specsRoot, markdownFiles, violations);
            }
            for (JavaMethod method : javaClass.getMethods()) {
                if (method.isAnnotatedWith(SpecContract.class)) {
                    SpecContract contract = method.getAnnotationOfType(SpecContract.class);
                    validateContract(
                            javaClass.getName() + "#" + method.getName(),
                            contract,
                            specsRoot,
                            markdownFiles,
                            violations
                    );
                }
            }
        }
        return violations;
    }

    private static void validateContract(
            String targetName,
            SpecContract contract,
            Path specsRoot,
            List<Path> markdownFiles,
            List<String> violations
    ) {
        String specId = contract.specId() == null ? "" : contract.specId().trim();
        if (!SPEC_ID_PATTERN.matcher(specId).matches()) {
            violations.add(targetName + " declares invalid specId '" + specId
                    + "' (expected pattern SPEC-NNN, e.g. SPEC-001)");
            return;
        }

        String explicitFile = contract.specFile() == null ? "" : contract.specFile().trim();
        if (!explicitFile.isEmpty()) {
            Path resolved = specsRoot.resolve(explicitFile).normalize();
            if (!resolved.startsWith(specsRoot.normalize()) || !Files.isRegularFile(resolved)) {
                violations.add(targetName + " references missing spec file '" + explicitFile
                        + "' for " + specId);
                return;
            }
            if (!fileContainsSpecId(resolved, specId)) {
                violations.add(targetName + " spec file '" + explicitFile
                        + "' does not mention specId '" + specId + "'");
            }
            return;
        }

        boolean foundInAny = markdownFiles.stream().anyMatch(md -> fileContainsSpecId(md, specId));
        if (!foundInAny) {
            violations.add(targetName + " references unbacked specId '" + specId
                    + "' (not found in any Markdown spec under " + specsRoot + ")");
        }
    }

    private static List<Path> listMarkdownFiles(Path specsRoot, List<String> violations) {
        try (Stream<Path> walk = Files.walk(specsRoot)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .toList();
        } catch (IOException ex) {
            violations.add("Failed to scan specs directory " + specsRoot + ": " + ex.getMessage());
            return List.of();
        }
    }

    private static boolean fileContainsSpecId(Path file, String specId) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).contains(specId);
        } catch (IOException ex) {
            return false;
        }
    }
}
