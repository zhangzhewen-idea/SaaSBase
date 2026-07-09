package com.saasbase.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.saasbase", importOptions = ImportOption.DoNotIncludeTests.class)
class ColaArchitectureTest {

    @ArchTest
    static final ArchRule domain_does_not_depend_on_outer_layers =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..adapter..",
                            "..application..",
                            "..infrastructure..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_does_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule adapter_does_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("..adapter..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true);
}
