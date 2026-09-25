package com.mercuriusxeno.goo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit convention enforcement. Only rules that catch recurring mistakes.
 */
class ConventionTest {

    private static final String[] DOMAIN_PACKAGES = {
            "com.mercuriusxeno.goo.block..", "com.mercuriusxeno.goo.item.."
    };
    private static final String LEVEL_LIGHT_ENGINE = "net.minecraft.world.level.lighting.LevelLightEngine";
    private static final String RUN_LIGHT_UPDATES = "runLightUpdates";
    private static final String[] RENDER_PACKAGES = {
            "com.mercuriusxeno.goo.client.ber..", "com.mercuriusxeno.goo.client.model.."
    };
    private static final String RENDER_TYPES = "net.minecraft.client.renderer.rendertype.RenderTypes";
    private static final String ENTITY_TRANSLUCENT = "entityTranslucent";
    private static JavaClasses mainClasses;
    private static JavaClasses testClasses;

    @BeforeAll
    static void importClasses() {
        mainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.mercuriusxeno.goo");
        testClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("com.mercuriusxeno.goo");
    }

    /**
     * Condition: class has methods or fields annotated with the given annotation.
     */
    private static ArchCondition<JavaClass> haveAnnotatedMembers(String annotationName) {
        return new ArchCondition<>("have members annotated with @" + extractSimpleName(annotationName)) {
            @Override
            public void check(JavaClass cls, ConditionEvents events) {
                cls.getMembers().stream()
                        .filter(m -> m.isAnnotatedWith(annotationName))
                        .forEach(m -> events.add(SimpleConditionEvent.violated(
                                m, m.getFullName() + " uses @" + extractSimpleName(annotationName))));
            }
        };
    }

    private static String extractSimpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    /**
     * Adapter-boundary classes that legitimately import GooItems, GooBlocks, or GooFluids.
     * Pattern B construction sites, fluid handlers, and registration code.
     */
    private static DescribedPredicate<JavaClass> registryAllowed() {
        return simpleName("CrucibleBlock")
                .or(simpleName("CanisterBlockEntity"))
                .or(simpleName("VatBlock"))
                .or(simpleName("GooCauldronInteractions"))
                .or(simpleName("BlobStacks"))
                .or(simpleName("GooOmniblobItem"))
                .or(simpleName("PartiallyMeltedItem"))
                .or(simpleName("GooFluidHandler"))
                .or(simpleName("HubFluidHandler"))
                .or(simpleName("PlayerInventorySlotHandler"))
                .or(simpleName("TapBlockEntity"))
                .or(simpleName("CanisterFluidHandler"))
                .or(simpleName("GasketInstallation"))
                .or(simpleName("TapBlock"))
                .or(simpleName("PlexerBlock"))
                .or(simpleName("HubBlock"))
                .or(simpleName("CanisterSlotLifecycle"))
                .or(simpleName("CrucibleInteraction"))
                .or(simpleName("CrucibleDrops"))
                .or(simpleName("TapInteractionHandler"))
                .or(simpleName("VatGasketOps"))
                .or(simpleName("ChainMarkerBlockEntity"))
                .or(simpleName("ICanisterHolder"))
                .or(simpleName("SlottedCanisterData"))
                .or(simpleName("CanisterSlot"))
                .or(simpleName("CanisterSlotFluidHandler"))
                .or(simpleName("CanisterFluidContent"))
                .or(simpleName("CanisterItem"));
    }

    // ── Decoupling boundary enforcement (decoupling-arch §5) ────────────

    /**
     * HARD RULE: no @OnlyIn anywhere - classes, methods, or fields.
     */
    @Test
    void noOnlyIn() {
        String annotation = "net.neoforged.api.distmarker.OnlyIn";
        noClasses()
                .should().beAnnotatedWith(annotation)
                .orShould(haveAnnotatedMembers(annotation))
                .because("HARD RULE: use dist executors or proxies, never @OnlyIn")
                .check(mainClasses);
    }

    /**
     * No main-source class drains the level light engine by hand: a server
     * level holds a ThreadedLevelLightEngine whose runLightUpdates throws,
     * and checkBlock already enqueues the recompute the engine's own tick runs.
     */
    @Test
    void noManualLightEngineDrain() {
        noClasses()
                .should().callMethodWhere(target(name(RUN_LIGHT_UPDATES))
                        .and(target(owner(assignableTo(LEVEL_LIGHT_ENGINE)))))
                .because("the server light engine drains its own queue on its tick;"
                        + " a manual drain throws (decision light-kick-never-drains)")
                .check(mainClasses);
    }

    /**
     * No renderer picks the translucent render type itself: every body and
     * fluid submits through GooSubmitter, and a translucent draw on its own
     * texture asks the submitter for the type. FULL_BRIGHT is a compile-time
     * constant javac inlines, so its guard is the checkstyle regexp on the
     * same packages rather than a bytecode rule.
     */
    @Test
    void renderTypeChoiceStaysInSubmitter() {
        noClasses()
                .that().resideInAnyPackage(RENDER_PACKAGES)
                .should().callMethodWhere(target(name(ENTITY_TRANSLUCENT))
                        .and(target(owner(name(RENDER_TYPES)))))
                .because("the render type for bodies and fluids lives in GooSubmitter"
                        + " (decision shared-submission-entry-point)")
                .check(mainClasses);
    }

    /**
     * No class is named for rune ink or a matrix upgrade (decisions rune-ink-stays-out, no-machine-takes-a-matrix).
     */
    @Test
    void runeInkHasNoClass() {
        noClasses()
                .should().haveSimpleNameContaining("RuneInk")
                .orShould().haveSimpleNameContaining("Matrix")
                .orShould().haveSimpleNameContaining("Matrices")
                .because("rune ink and matrix upgrades are removed from the mod"
                        + " (decisions rune-ink-stays-out, no-machine-takes-a-matrix)")
                .check(mainClasses);
    }

    /**
     * Test classes should be package-private.
     */
    @Test
    void testClassesArePackagePrivate() {
        classes()
                .that().haveSimpleNameEndingWith("Test")
                .should().notBePublic()
                .because("JUnit 5 doesn't need public test classes (TEST-ETHOS)")
                .check(testClasses);
    }

    /**
     * No new domain-layer class may import GooItems, GooBlocks, or GooFluids.
     */
    @Test
    void domainLayerDoesNotImportBannedRegistryClasses() {
        noClasses()
                .that().resideInAnyPackage(DOMAIN_PACKAGES)
                .and(DescribedPredicate.not(registryAllowed()))
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.mercuriusxeno.goo.registry.GooItems")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("com.mercuriusxeno.goo.registry.GooBlocks")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("com.mercuriusxeno.goo.registry.GooFluids")
                .because("domain layer must not import registry singletons (decoupling-arch §5.1)")
                .check(mainClasses);
    }

    /**
     * Goo.GOO_VALUES must only appear in adapter wrappers, not domain logic.
     */
    @Test
    void domainLayerDoesNotAccessGooValues() {
        noClasses()
                .that().resideInAnyPackage(DOMAIN_PACKAGES)
                .and(DescribedPredicate.not(
                        simpleName("CrucibleBlockEntity").or(simpleName("PlexerBlockEntity"))
                                .or(simpleName("CrucibleInsertion"))))
                .should().accessField(Goo.class, "GOO_VALUES")
                .because("domain layer must use IGooValueLookup seams, not Goo.GOO_VALUES (decoupling-arch §5.2)")
                .check(mainClasses);
    }

    /**
     * BuiltInRegistries must only appear in adapter wrappers, not domain logic.
     */
    @Test
    void domainLayerDoesNotUseBuiltInRegistries() {
        noClasses()
                .that().resideInAnyPackage(DOMAIN_PACKAGES)
                .and(DescribedPredicate.not(
                        simpleName("ContainerEvaluator")
                                .or(simpleName("CrucibleBlockEntity"))
                                .or(simpleName("CrucibleInsertion"))
                                .or(simpleName("PlexerBlockEntity"))
                                .or(belongToAnyOf(com.mercuriusxeno.goo.item.CanisterFluidContent.class))))
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("net.minecraft.core.registries.BuiltInRegistries")
                .because("domain layer must resolve Identifiers in adapter wrappers (decoupling-arch §5.3)")
                .check(mainClasses);
    }
}
