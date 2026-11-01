package com.pvpwow.lfg.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The architecture rules, executable. Until this class existed, "the domain
 * has zero framework imports" was checked ONCE with a manual grep, and nothing
 * stopped the next commit from breaking it. Now a violation fails the build
 * and names the offending dependency.
 *
 * <p>Uses ArchUnit's core API ({@link ClassFileImporter} + rules checked from
 * plain {@code @Test} methods) rather than the {@code archunit-junit5} engine,
 * so the rules run under JUnit 6 with zero engine coupling. The import
 * excludes {@code target/test-classes}, so the rules judge production classes
 * only — test classes may of course import JUnit and ArchUnit itself.
 */
class ArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.pvpwow.lfg");

    @Test
    void theDomainDependsOnNoFramework() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "org.hibernate..",
                        "com.fasterxml..",
                        "org.slf4j..")
                .because("the domain must stay runnable and testable without any framework; "
                        + "framework contracts change without asking us, and the whole point "
                        + "of the hexagon is that frameworks are edges, never the core")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void theDomainNeverReachesOutward() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapters..",
                        "..application..")
                .because("dependencies point inward: adapters and application services know "
                        + "the domain, never the reverse. ArmoryLinks (adapters.in.rest) may "
                        + "import Region (domain); the opposite direction would couple "
                        + "business rules to the web")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void domainPackagesAreFreeOfCycles() {
        slices().matching("com.pvpwow.lfg.domain.(*)..")
                .should().beFreeOfCycles()
                .because("a cycle between domain packages (listing depending on character AND "
                        + "character on listing) does not mean the imports are wrong - it "
                        + "means the aggregate boundaries are wrong")
                .check(PRODUCTION_CLASSES);
    }
}
