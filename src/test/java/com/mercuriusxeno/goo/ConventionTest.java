package com.mercuriusxeno.goo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.mercuriusxeno.goo.tools.architecture.ArchitectureGraphs;
import net.minecraft.world.level.lighting.LevelLightEngine;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * ArchUnit convention enforcement. Only rules that catch recurring mistakes.
 */
class ConventionTest {

    private static final String[] DOMAIN_PACKAGES = {
            "com.mercuriusxeno.goo.block..", "com.mercuriusxeno.goo.item.."
    };
    private static final String RUN_LIGHT_UPDATES = "runLightUpdates";
    private static final String CLIENT_PACKAGE = "com.mercuriusxeno.goo.client..";
    private static final String CLIENT_OVERLAY_PACKAGE = "com.mercuriusxeno.goo.client.overlay..";
    private static final String CLIENT_HUD_PACKAGE = "com.mercuriusxeno.goo.client.hud..";
    private static final String MACHINE_BLOCK = "com.mercuriusxeno.goo.block.GooMachineBlock";
    private static final String MACHINE_BLOCK_ENTITY = "com.mercuriusxeno.goo.block.GooMachineBlockEntity";
    private static final String PLEXER_BLOCK_ENTITY = "com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity";
    private static final String ITEM_PACKAGE = "com.mercuriusxeno.goo.item.";
    private static final String GOO_SOURCE_SCANNER = ITEM_PACKAGE + "GooSourceScanner";
    private static final String GOO_SUBMITTER = "com.mercuriusxeno.goo.client.GooSubmitter";
    private static final String RENDER_TYPES = "net.minecraft.client.renderer.rendertype.RenderTypes";
    private static final String ENTITY_TRANSLUCENT = "entityTranslucent";
    private static final String ENTITY_SOLID = "entitySolid";
    private static final String[] SHARED_PACKAGES = {
            "com.mercuriusxeno.goo", "com.mercuriusxeno.goo.block..", "com.mercuriusxeno.goo.item..",
            "com.mercuriusxeno.goo.ability..", "com.mercuriusxeno.goo.data..", "com.mercuriusxeno.goo.network..",
            "com.mercuriusxeno.goo.registry..", "com.mercuriusxeno.goo.command..", "com.mercuriusxeno.goo.lab..",
            "com.mercuriusxeno.goo.fluid.."
    };
    private static final String[] CLIENT_PACKAGES = {
            "net.minecraft.client..", "com.mercuriusxeno.goo.client..", "com.mojang.blaze3d.."
    };
    private static final String GOO_PATH = "/com/mercuriusxeno/goo/";
    /**
     * The main and tools classes split into slices imported side by side, since one
     * import of them all took seconds (decision slow-tests-take-a-seam). The first
     * slice holds every client reader beside the block and item types it must not
     * check instanceof against, so assignableTo sees their whole goo hierarchy.
     */
    private static final List<ImportOption> MAIN_SLICES = List.of(
            inAny("block/", "item/", "client/overlay/", "client/hud/"),
            inAny("client/ability/"),
            location -> location.contains(GOO_PATH + "client/") && !inAny("client/ability/", "client/overlay/",
                    "client/hud/").includes(location),
            inAny("ability/"),
            location -> !inAny("block/", "item/", "client/", "ability/").includes(location));
    private static final String EMPTY_SHOULD = "failed to check any classes";
    private static List<JavaClasses> mainSlices;
    private static JavaClasses testClasses;

    @BeforeAll
    static void importClasses() {
        Set<Location> mainAndTools = Set.of(locationOf(Goo.class), locationOf(ArchitectureGraphs.class));
        List<CompletableFuture<JavaClasses>> slices = MAIN_SLICES.stream()
                .map(slice -> CompletableFuture.supplyAsync(() ->
                        new ClassFileImporter().withImportOption(slice).importLocations(mainAndTools)))
                .toList();
        // A test class's simple name ends in Test, and testClassesArePackagePrivate reads no other.
        testClasses = new ClassFileImporter().withImportOption(location -> location.contains("Test.class"))
                .importLocations(Set.of(locationOf(ConventionTest.class)));
        mainSlices = slices.stream().map(CompletableFuture::join).toList();
    }

    /**
     * A class the JVM loads, without initializing it, as a subtype of the given type;
     * the import resolves no Minecraft hierarchy, so the JVM answers for it.
     */
    private static DescribedPredicate<JavaClass> loadedAssignableTo(Class<?> type) {
        return DescribedPredicate.describe("assignable to " + type.getName(), cls -> {
            try {
                return type.isAssignableFrom(Class.forName(cls.getName(), false, ConventionTest.class.getClassLoader()));
            } catch (ClassNotFoundException | LinkageError absent) {
                return false;
            }
        });
    }

    private static ImportOption inAny(String... packagePaths) {
        return location -> Arrays.stream(packagePaths).anyMatch(path -> location.contains(GOO_PATH + path));
    }

    private static Location locationOf(Class<?> member) {
        try {
            return Location.of(member.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(member + " has no classes directory location", e);
        }
    }

    /**
     * Checks a rule over every main slice: it fails on any violation in any slice,
     * and on a rule whose classes no slice holds, as one check over all of main would.
     */
    private static void checkMain(ArchRule rule) {
        List<String> violations = new ArrayList<>();
        int slicesChecked = 0;
        for (JavaClasses slice : mainSlices) {
            try {
                violations.addAll(rule.evaluate(slice).getFailureReport().getDetails());
                slicesChecked++;
            } catch (AssertionError empty) {
                if (!String.valueOf(empty.getMessage()).contains(EMPTY_SHOULD)) {
                    throw empty;
                }
            }
        }
        assertNotEquals(0, slicesChecked, () -> rule.getDescription() + " " + EMPTY_SHOULD);
        assertEquals(List.of(), violations, rule::getDescription);
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
                .or(simpleName("GooStacks"))
                .or(simpleName("GooItem"))
                .or(simpleName("ChrysmItem"))
                .or(simpleName("PartiallyMeltedItem"))
                .or(simpleName("GooFluidHandler"))
                .or(simpleName("HubFluidHandler"))
                .or(simpleName("PlayerInventorySlotHandler"))
                .or(simpleName("CanisterFluidHandler"))
                .or(simpleName("GasketInstallation"))
                .or(simpleName("PlexerBlock"))
                .or(simpleName("HubBlock"))
                .or(simpleName("CanisterSlotLifecycle"))
                .or(simpleName("CrucibleInteraction"))
                .or(simpleName("CrucibleDrops"))
                .or(simpleName("VatGasketOps"))
                .or(simpleName("AbilityBlockEntity"))
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
        checkMain(noClasses()
                .should().beAnnotatedWith(annotation)
                .orShould(haveAnnotatedMembers(annotation))
                .because("HARD RULE: use dist executors or proxies, never @OnlyIn"));
    }

    /**
     * No main-source class drains the level light engine by hand: a server
     * level holds a ThreadedLevelLightEngine whose runLightUpdates throws,
     * and checkBlock already enqueues the recompute the engine's own tick runs.
     */
    @Test
    void noManualLightEngineDrain() {
        checkMain(noClasses()
                .should().callMethodWhere(target(name(RUN_LIGHT_UPDATES))
                        .and(target(owner(loadedAssignableTo(LevelLightEngine.class)))))
                .because("the server light engine drains its own queue on its tick;"
                        + " a manual drain throws (decision light-kick-never-drains)"));
    }

    /**
     * No class under client but GooSubmitter picks an entity render type
     * itself: every goo draw, a body, a fluid, a gasket cap, a thrown goo or
     * an ability visual, asks the submitter for it. FULL_BRIGHT is a
     * compile-time constant javac inlines, so its guard is the checkstyle
     * regexp over the same packages rather than a bytecode rule.
     */
    @Test
    void renderTypeChoiceStaysInSubmitter() {
        checkMain(noClasses()
                .that().resideInAPackage(CLIENT_PACKAGE)
                .and().doNotHaveFullyQualifiedName(GOO_SUBMITTER)
                .should().callMethodWhere(target(name(ENTITY_TRANSLUCENT).or(name(ENTITY_SOLID)))
                        .and(target(owner(name(RENDER_TYPES)))))
                .because("every goo render type lives in GooSubmitter"
                        + " (decision submitter-owns-every-render-choice)"));
    }

    /**
     * No class under client.overlay or client.hud checks instanceof against a
     * machine block or block entity type: each dispatches on ICanisterHolder,
     * IGasketHolder or another host interface, so a new machine joins every
     * outline, preview, HUD and gasket overlay by implementing them (decision
     * hosts-answer-bounds-through-interfaces).
     */
    @Test
    void clientReadersDispatchOnHostInterfaces() {
        checkMain(classes()
                .that().resideInAnyPackage(CLIENT_OVERLAY_PACKAGE, CLIENT_HUD_PACKAGE)
                .should(checkNoInstanceofOn(machineType()))
                .because("client readers dispatch on the host interfaces"
                        + " (decision hosts-answer-bounds-through-interfaces)"));
    }

    /**
     * GooSourceScanner reads every goo-carrying item through GooCarrierItem and
     * checks no carrier item class itself, so a new carrier joins the scan by
     * implementing the interface (decision hosts-answer-bounds-through-interfaces).
     */
    @Test
    void sourceScannerDispatchesOnTheCarrierInterface() {
        checkMain(classes()
                .that().haveFullyQualifiedName(GOO_SOURCE_SCANNER)
                .should(checkNoInstanceofOn(carrierItemClass()))
                .because("the scan reads carriers through GooCarrierItem"
                        + " (decision hosts-answer-bounds-through-interfaces)"));
    }

    private static DescribedPredicate<JavaClass> carrierItemClass() {
        return assignableTo(ITEM_PACKAGE + "GooItem")
                .or(assignableTo(ITEM_PACKAGE + "CanisterItem")).or(assignableTo(ITEM_PACKAGE + "VatBlockItem"))
                .or(assignableTo(ITEM_PACKAGE + "HubBlockItem"))
                .as("a goo carrier item class");
    }

    private static DescribedPredicate<JavaClass> machineType() {
        return assignableTo(MACHINE_BLOCK).or(assignableTo(MACHINE_BLOCK_ENTITY)).or(assignableTo(PLEXER_BLOCK_ENTITY))
                .as("a machine block or block entity type");
    }

    private static ArchCondition<JavaClass> checkNoInstanceofOn(DescribedPredicate<JavaClass> forbidden) {
        return new ArchCondition<>("check no instanceof against " + forbidden.getDescription()) {
            @Override
            public void check(JavaClass cls, ConditionEvents events) {
                cls.getCodeUnits().stream()
                        .flatMap(unit -> unit.getInstanceofChecks().stream())
                        .filter(check -> forbidden.test(check.getRawType()))
                        .forEach(check -> events.add(SimpleConditionEvent.violated(check,
                                check.getOwner().getFullName() + " checks instanceof "
                                        + check.getRawType().getName() + " " + check.getSourceCodeLocation())));
            }
        };
    }

    /**
     * No class is named for rune ink or a matrix upgrade (decisions rune-ink-stays-out, no-machine-takes-a-matrix).
     */
    @Test
    void runeInkHasNoClass() {
        checkMain(noClasses()
                .should().haveSimpleNameContaining("RuneInk")
                .orShould().haveSimpleNameContaining("Matrix")
                .orShould().haveSimpleNameContaining("Matrices")
                .because("rune ink and matrix upgrades are removed from the mod"
                        + " (decisions rune-ink-stays-out, no-machine-takes-a-matrix)"));
    }

    /**
     * No class in a shared package links client-only code, so a dedicated
     * server that verifies or scans a shared class never reaches a Screen,
     * Minecraft or a render type (decision client-handlers-under-client-network).
     * Client-bound handlers live under client.network, the glove reaches the
     * client through ISidedProxy, and the client mixin sits in mixin, outside
     * the shared set. Javadoc links never reach bytecode, so ISidedProxy's link
     * to GooClientSetup passes. The dev runs load the joined jar, where every
     * client class resolves, so no run shows this crash; this rule is the proof.
     */
    @Test
    void sharedPackagesDoNotLinkClientCode() {
        checkMain(noClasses()
                .that().resideInAnyPackage(SHARED_PACKAGES)
                .should().dependOnClassesThat()
                .resideInAnyPackage(CLIENT_PACKAGES)
                .because("a dedicated server has no client classes to link"
                        + " (decision client-handlers-under-client-network)"));
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
        checkMain(noClasses()
                .that().resideInAnyPackage(DOMAIN_PACKAGES)
                .and(DescribedPredicate.not(registryAllowed()))
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.mercuriusxeno.goo.registry.GooItems")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("com.mercuriusxeno.goo.registry.GooBlocks")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("com.mercuriusxeno.goo.registry.GooFluids")
                .because("domain layer must not import registry singletons (decoupling-arch §5.1)"));
    }

    /**
     * Only adapter wrappers resolve goo values from a level; domain logic takes
     * an IGooValueLookup (decision type-package-and-per-server-holders).
     */
    @Test
    void domainLayerDoesNotResolveGooValues() {
        checkMain(noClasses()
                .that().resideInAnyPackage(DOMAIN_PACKAGES)
                .and(DescribedPredicate.not(
                        simpleName("CrucibleBlockEntity").or(simpleName("PlexerBlockEntity"))
                                .or(simpleName("CrucibleInsertion"))))
                .should().dependOnClassesThat().haveFullyQualifiedName("com.mercuriusxeno.goo.data.GooValues")
                .because("domain layer must use IGooValueLookup seams, not GooValues (decoupling-arch §5.2)"));
    }

    /**
     * BuiltInRegistries must only appear in adapter wrappers, not domain logic.
     */
    @Test
    void domainLayerDoesNotUseBuiltInRegistries() {
        checkMain(noClasses()
                .that().resideInAnyPackage(DOMAIN_PACKAGES)
                .and(DescribedPredicate.not(
                        simpleName("ContainerEvaluator")
                                .or(simpleName("CrucibleBlockEntity"))
                                .or(simpleName("CrucibleInsertion"))
                                .or(simpleName("PlexerBlockEntity"))
                                .or(belongToAnyOf(com.mercuriusxeno.goo.item.CanisterFluidContent.class))))
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("net.minecraft.core.registries.BuiltInRegistries")
                .because("domain layer must resolve Identifiers in adapter wrappers (decoupling-arch §5.3)"));
    }

    /**
     * The throw path reads the ability's delivery kind, never the glow type, to
     * decide a throw's flight, sound or aim line (decision standing-abilities-name-arc-or-beam).
     */
    @Test
    void throwPathReadsNoGlowType() {
        checkMain(noClasses()
                .that().resideInAnyPackage("com.mercuriusxeno.goo.network..",
                        "com.mercuriusxeno.goo.client.throwing..", CLIENT_OVERLAY_PACKAGE)
                .should().accessField("com.mercuriusxeno.goo.type.GooTypes", "GLOW")
                .because("the delivery kind decides the throw, not the goo type"));
    }
}
