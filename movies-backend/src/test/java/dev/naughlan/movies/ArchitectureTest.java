package dev.naughlan.movies;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Design rules from step 1.4, checked on every build instead of remembered.
 */
class ArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.naughlan.movies");

    @Test
    void packagesHaveNoDependencyCycles() {
        slices().matching("dev.naughlan.movies.(*)..")
                .should().beFreeOfCycles()
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void moviesDoNotKnowAboutReviews() {
        // Reviews point at movies (by imdbId); a movie never points back
        noClasses().that().resideInAPackage("dev.naughlan.movies.movie..")
                .should().dependOnClassesThat().resideInAPackage("dev.naughlan.movies.review..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void commonCodeDoesNotDependOnFeatures() {
        noClasses().that().resideInAPackage("dev.naughlan.movies.common..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.naughlan.movies.movie..",
                        "dev.naughlan.movies.review..",
                        "dev.naughlan.movies.tmdb..",
                        "dev.naughlan.movies.user..")
                .check(PRODUCTION_CLASSES);
    }
}
