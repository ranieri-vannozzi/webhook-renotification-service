package it.ranierivannozzi.webhook;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the hexagonal package boundaries.
 * <p>
 * Empty packages are tolerated so the rules hold while the structure is still being filled in.
 */
@AnalyzeClasses(packages = "it.ranierivannozzi.webhook", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

	@ArchTest
	static final ArchRule hexagonalLayers = onionArchitecture()
			.domainModels("..domain.model..")
			.domainServices("..domain.resolution..")
			.applicationServices("..application..")
			.adapter("web", "..adapter.in.web..")
			.adapter("dataset", "..adapter.out.dataset..")
			.adapter("queue", "..adapter.out.queue..")
			.withOptionalLayers(true);

	@ArchTest
	static final ArchRule domainIsFrameworkFree = classes()
			.that().resideInAPackage("..domain..")
			.should().onlyDependOnClassesThat()
			.resideInAnyPackage("java..", "org.jspecify..", "..domain..")
			.allowEmptyShould(true);

	/**
	 * The SLF4J logging facade is the only library allowed besides the domain: it is not a framework and keeps the
	 * application free of Spring.
	 */
	@ArchTest
	static final ArchRule applicationIsFrameworkFree = classes()
			.that().resideInAPackage("..application..")
			.should().onlyDependOnClassesThat()
			.resideInAnyPackage("java..", "org.jspecify..", "org.slf4j..", "..domain..", "..application..")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule adaptersGoThroughPorts = noClasses()
			.that().resideInAPackage("..adapter..")
			.should().dependOnClassesThat().resideInAPackage("..application.service..")
			.allowEmptyShould(true);

}
