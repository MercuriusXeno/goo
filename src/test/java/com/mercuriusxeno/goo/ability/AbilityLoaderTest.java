package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AfterimageStep;
import com.mercuriusxeno.goo.ability.program.AirbornStep;
import com.mercuriusxeno.goo.ability.program.BranchStep;
import com.mercuriusxeno.goo.ability.program.CharmStep;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.FloatStep;
import com.mercuriusxeno.goo.ability.program.GhostTrailStep;
import com.mercuriusxeno.goo.ability.program.HostVariables;
import com.mercuriusxeno.goo.ability.program.LingerStep;
import com.mercuriusxeno.goo.ability.program.PlaceBlockStep;
import com.mercuriusxeno.goo.ability.program.PotionStep;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.ability.program.TeleportStep;
import com.mercuriusxeno.goo.ability.program.TomeKind;
import com.mercuriusxeno.goo.ability.program.TomeStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.data.IdentifiedJsonScan;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every shipped ability file scanned the way {@link AbilityLoader} scans it, through a
 * mocked resource manager, decodes into a definition carrying its file id from
 * construction (decision delete-dead-fold-mirrors).
 */
class AbilityLoaderTest {

    private static final String DIRECTORY = "goo_abilities";
    private static final int CHARM_ROLLS = 1000;
    private static final int RARE_CHARMS = 50;
    private static final double ZOMBIE_MAX_HEALTH = 20;
    private static final double TOP_MAX_HEALTH = 1024;
    private static final double TAP_SPAWN_CHANCE = 5;
    /** The proving example of a world ability that stays after its blob lands. */
    private static final String LINGERING = "crystal_cloud";
    /** The ability whose program ends the tick it lands. */
    private static final String BLAST = "unstable_explode";
    /** The abilities whose whole design was a per-stack shape. */
    private static final List<String> STACK_SHAPE_ABILITIES = List.of("blaze_flat", "blaze_tunnel",
            "frost_flat", "frost_tunnel", "frost_sphere", "rock_flat", "rock_tunnel");

    private static Map<Identifier, AbilityDefinition> scanShipped(List<Path> files) {
        return IdentifiedJsonScan.scan(managerListing(files), FileToIdConverter.json(DIRECTORY), JsonOps.INSTANCE,
                AbilityDefinition::codecFor);
    }

    /**
     * The item paths a class declares, read from its static field names, so the suite loads
     * no registry and calls no Bootstrap; reflecting on field names leaves the class uninitialized.
     */
    private static Set<String> declaredItemPaths(Class<?> itemHolder) {
        return Stream.of(itemHolder.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    @Test
    void everyScannedAbilityCarriesItsFileId() {
        List<Path> files = AbilityJson.files();
        assertFalse(files.isEmpty(), "No ability JSON found under " + AbilityJson.ABILITIES_DIR);

        Map<Identifier, AbilityDefinition> scanned = scanShipped(files);

        assertEquals(files.size(), scanned.size(), "every ability file decodes");
        for (Path file : files) {
            Identifier fileId = AbilityJson.idOf(file.getFileName().toString());
            assertEquals(fileId, scanned.get(fileId).id(), file.getFileName().toString());
        }
    }

    // decision airborn-steerable-levitation-and-soft-falls
    @Test
    void typhoonAirbornNamesItsGlideSpeedAndSteer() {
        AirbornStep airborn = AbilityJson.decode("typhoon_airborn").behaviors().stream()
                .filter(AirbornStep.class::isInstance).map(AirbornStep.class::cast).findFirst().orElseThrow();

        assertEquals(Expr.literal(1.2), airborn.glideSpeed());
        assertEquals(Expr.literal(0.05), airborn.glideSteer());
        assertEquals(Expr.literal(1.5), airborn.jetBoost());
    }

    // decision splat-runs-the-program-no-fuse
    @Test
    void stackShapeAbilitiesLeaveTheRegistry() {
        Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());

        assertFalse(scanned.isEmpty(), "No ability scanned");
        for (String name : STACK_SHAPE_ABILITIES) {
            assertFalse(scanned.containsKey(Identifier.fromNamespaceAndPath(Goo.MODID, name)), name + " still loads");
        }
    }

    // decision wither-ability-deleted
    @Test
    void netherHoldsNoWitherAbility() {
        Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());

        assertTrue(scanned.values().stream().anyMatch(def -> def.gooType() == GooTypes.NETHER), "No nether ability scanned");
        assertTrue(scanned.values().stream().filter(def -> def.gooType() == GooTypes.NETHER)
                .noneMatch(def -> def.id().getPath().contains("wither")), "a nether wither ability still loads");
    }

    // decision float-blob-levitates-the-mob
    @Test
    void typhoonFloatReplacesLevitateWithAFloatStep() {
        Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());
        AbilityDefinition floatAbility = scanned.get(Identifier.fromNamespaceAndPath(Goo.MODID, "typhoon_float"));

        assertFalse(scanned.containsKey(Identifier.fromNamespaceAndPath(Goo.MODID, "typhoon_levitate")),
                "typhoon_levitate still loads");
        assertEquals("goo.ability.typhoon.float", floatAbility.displayName());
        assertTrue(floatAbility.behaviors().stream().anyMatch(FloatStep.class::isInstance), "no float step");
    }

    // decision jet-pushes-along-the-look-while-held
    @Test
    void typhoonTailwindReplacesPropelAsAChannelOnTheSelf() {
        Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());
        AbilityDefinition tailwind = scanned.get(Identifier.fromNamespaceAndPath(Goo.MODID, "typhoon_tailwind"));

        assertFalse(scanned.containsKey(Identifier.fromNamespaceAndPath(Goo.MODID, "typhoon_propel")),
                "typhoon_propel still loads");
        assertTrue(HeldRoute.channelsOnSelf(tailwind.delivery(), tailwind.badge()),
                "typhoon_tailwind does not channel on the self");
    }

    // decision splat-runs-the-program-no-fuse
    @Test
    void noShippedProgramReadsAStackCount() {
        for (AbilityDefinition ability : scanShipped(AbilityJson.files()).values()) {
            Set<String> read = ability.behaviors().stream()
                    .flatMap(AbilityLoaderTest::withDescendants)
                    .flatMap(Step::expressions)
                    .flatMap(expr -> expr.variables().stream())
                    .collect(Collectors.toSet());
            assertFalse(read.contains("stacks") || read.contains("max_stacks"), ability.id() + " reads " + read);
        }
    }

    // decision lingering-abilities-place-their-own-thing
    // decision one-proving-ability-per-kind
    @Test
    void eachLingeringAbilityPlacesItsOwnThingAndBlastPlacesNothing() {
        assertTrue(placesItsOwnThing(AbilityJson.decode(LINGERING)), LINGERING + " names no step placing its own thing");
        assertFalse(placesItsOwnThing(AbilityJson.decode(BLAST)), BLAST + " places a thing of its own");
    }

    private static boolean placesItsOwnThing(AbilityDefinition ability) {
        return ability.behaviors().stream().flatMap(AbilityLoaderTest::withDescendants)
                .anyMatch(step -> step instanceof LingerStep || step instanceof PlaceBlockStep);
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(AbilityLoaderTest::withDescendants));
    }

    /**
     * Every item a shipped ability's gate names is one the game declares, vanilla or goo
     * (decisions ability-hidden-until-recipes-known, tests-read-the-inventory-from-loaded-json).
     */
    @Test
    void everyShippedAbilityRequiresTheItemsItsGateNames() {
        Map<String, Set<String>> declared = Map.of(
                Identifier.DEFAULT_NAMESPACE, declaredItemPaths(Items.class),
                Goo.MODID, declaredItemPaths(GooItems.class));

        for (AbilityDefinition ability : scanShipped(AbilityJson.files()).values()) {
            for (Identifier item : ability.requires()) {
                assertTrue(declared.getOrDefault(item.getNamespace(), Set.of()).contains(item.getPath()),
                        ability.id() + " requires " + item + ", which no item class declares");
            }
        }
    }

    /**
     * Crystal's Prism and Glitter take no item cost (operator ruling
     * 2026-10-10), and an ability naming no reagent consumes nothing
     * (decisions ability-json-names-its-reagent, glitter-sphere-icons-gem-ore-groups).
     */
    @Test
    void prismAndGlitterConsumeNothing() {
        Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());

        for (String path : List.of("crystal_prism", "crystal_glitter", "crystal_cloud")) {
            assertEquals(List.of(), scanned.get(Identifier.fromNamespaceAndPath(Goo.MODID, path)).consumes(), path);
        }
    }

    /**
     * Every shipped self + brew ability names an upkeep in place of a one-shot
     * cost, and every other ability names no upkeep
     * (decision self-effects-trickle-until-ended).
     */
    @Test
    void selfBrewAbilitiesNameAnUpkeepInPlaceOfACost() {
        Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());

        for (AbilityDefinition ability : scanned.values()) {
            if (SelfEatRoute.eats(ability.delivery(), ability.badge())) {
                assertEquals(0, ability.cost(), ability.id().toString());
                assertTrue(ability.upkeep() >= 1, ability.id().toString());
            } else {
                assertEquals(AbilityDefinition.NO_UPKEEP, ability.upkeep(), ability.id().toString());
            }
        }
    }

    /**
     * Stoneskin's up sound is the petrify sound Statues plays, at another
     * pitch, and Gluttony's is a healing cue of its own
     * (decision held-effects-sound-up-and-down).
     */
    @Test
    void heldEffectsSoundTheirOwnUpCue() {
        SoundStep stoneskinUp = firstSound("rock_stoneskin");
        assertEquals(Identifier.withDefaultNamespace("block.deepslate.place"), stoneskinUp.sound());
        assertNotEquals(1.0, stoneskinUp.pitch().evaluate(Variables.NONE), 1e-6);
        assertNotEquals(0.8, stoneskinUp.pitch().evaluate(Variables.NONE), 1e-6);
        assertTrue(AbilityJson.decode("jelly_gluttony").behaviors().stream().anyMatch(SoundStep.class::isInstance));
    }

    private static SoundStep firstSound(String name) {
        return AbilityJson.decode(name).behaviors().stream().filter(SoundStep.class::isInstance)
                .map(SoundStep.class::cast).findFirst().orElseThrow();
    }

    /**
     * Hex charm charms the struck mob in place of weakening it
     * (decision charm-glisten-and-icon-over-the-head).
     */
    @Test
    void hexCharmCharmsInPlaceOfWeakness() {
        List<Step> steps = AbilityJson.decode("hex_charm").behaviors().stream()
                .flatMap(AbilityLoaderTest::stepTree).toList();

        assertTrue(steps.stream().anyMatch(CharmStep.class::isInstance), "no charm step");
        assertTrue(steps.stream().noneMatch(PotionStep.class::isInstance), "still applies a potion");
    }

    /**
     * Hex enchant consumes one book and plays a hex afterimage on the player
     * (decision enchant-book-with-a-purple-afterimage).
     */
    @Test
    void hexEnchantTakesABookUnderAHexAfterimage() {
        AbilityDefinition enchant = AbilityJson.decode("hex_enchant");

        assertEquals(List.of(Identifier.withDefaultNamespace("book")), enchant.consumes());
        assertEquals(List.of(GooTypes.HEX), afterimageTypes(enchant.behaviors()));
    }


    /**
     * Enchant and Fuse each play their tome once their own step has acted
     * (decisions enchant-book-with-a-purple-afterimage, fuse-two-books-for-hex-goo).
     */
    @ParameterizedTest
    @CsvSource({"hex_enchant, ENCHANT", "hex_fuse, FUSE"})
    void bookAbilitiesPlayTheirTome(String name, TomeKind kind) {
        assertEquals(List.of(kind), AbilityJson.decode(name).behaviors().stream()
                .filter(TomeStep.class::isInstance).map(step -> ((TomeStep) step).kind()).toList(), name);
    }

    /**
     * Every brew sounds as its effect starts, as every held effect sounds as
     * it ends (decision held-effects-sound-up-and-down).
     */
    @ParameterizedTest
    @MethodSource("shippedBrews")
    void everyBrewSoundsAsItStarts(AbilityDefinition brew) {
        assertTrue(brew.behaviors().stream().flatMap(AbilityLoaderTest::stepTree).anyMatch(SoundStep.class::isInstance),
                brew.id().toString());
    }

    /** Every shipped self + brew ability (decision tests-read-the-inventory-from-loaded-json). */
    static Stream<AbilityDefinition> shippedBrews() {
        return AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(ability -> SelfEatRoute.eats(ability.delivery(), ability.badge()));
    }

    /**
     * Hex charm lands on every zombie, max health 20, and on few mobs at
     * vanilla's top max health, 1024: of a thousand rolls at 1024, where
     * pow(20 / max_health, 1.5) expects under three, fewer than fifty land
     * (decision charm-glisten-and-icon-over-the-head).
     */
    @Test
    void hexCharmIsResistedByHighHealth() {
        Expr chance = AbilityJson.decode("hex_charm").behaviors().stream()
                .flatMap(AbilityLoaderTest::stepTree).filter(BranchStep.class::isInstance)
                .map(step -> ((BranchStep) step).when()).findFirst().orElseThrow();

        assertEquals(CHARM_ROLLS, landedCharms(chance, ZOMBIE_MAX_HEALTH));
        assertTrue(landedCharms(chance, TOP_MAX_HEALTH) < RARE_CHARMS, "high health should rarely be charmed");
    }

    private static long landedCharms(Expr chance, double maxHealth) {
        Variables mob = name -> HostVariables.MAX_HEALTH.equals(name) ? OptionalDouble.of(maxHealth)
                : OptionalDouble.empty();
        return IntStream.range(0, CHARM_ROLLS).filter(roll -> chance.evaluate(mob) != 0).count();
    }

    /**
     * Ender blink leaves an ender afterimage where the player stood, before
     * its teleport, and another where it lands, after it
     * (decision afterimage-is-one-shared-effect).
     */
    @Test
    void enderBlinkLeavesAnAfterimageAtSourceAndTarget() {
        List<Step> steps = AbilityJson.decode("ender_blink").behaviors();
        int teleport = steps.indexOf(steps.stream().filter(TeleportStep.class::isInstance).findFirst().orElseThrow());

        assertEquals(List.of(GooTypes.ENDER), afterimageTypes(steps.subList(0, teleport)), "no ripple at the source");
        assertEquals(List.of(GooTypes.ENDER), afterimageTypes(steps.subList(teleport + 1, steps.size())),
                "no ripple at the target");
    }

    /**
     * Ender blink lays an ender ghost trail after its teleport, beside the
     * ripple at both ends, which it adds to and replaces nothing of
     * (decision ghost-trail-spans-the-blink).
     */
    @Test
    void enderBlinkLaysAGhostTrailAfterItsTeleportBesideTheRipple() {
        List<Step> steps = AbilityJson.decode("ender_blink").behaviors();
        int teleport = steps.indexOf(steps.stream().filter(TeleportStep.class::isInstance).findFirst().orElseThrow());
        List<Step> after = steps.subList(teleport + 1, steps.size());

        assertEquals(List.of(GooTypes.ENDER), after.stream().filter(GhostTrailStep.class::isInstance)
                .map(step -> ((GhostTrailStep) step).goo()).toList(), "no ender ghost trail after the teleport");
        assertTrue(steps.subList(0, teleport).stream().noneMatch(GhostTrailStep.class::isInstance),
                "a ghost trail runs before the jump it traces");
        assertEquals(1, afterimageTypes(steps.subList(0, teleport)).size(), "the source ripple is gone");
        assertEquals(1, afterimageTypes(after).size(), "the destination ripple is gone");
    }

    /**
     * Ender blink's indicator shows while right click is held, an ability
     * naming no indicator shows while held too, and the client's preview reads
     * the range the server's teleport jumps (decision ripple-outline-is-the-blink-cursor).
     */
    @Nested
    class Indicator {

        @Test
        void enderBlinkNamesItsIndicatorHeld() {
            assertTrue(AbilityJson.read("ender_blink").contains("\"indicator\": \"held\""),
                    "ender_blink names no held indicator");
            assertEquals(IndicatorShowing.HELD, AbilityJson.decode("ender_blink").indicator());
        }

        @Test
        void anAbilityNamingNoIndicatorShowsWhileHeld() {
            assertFalse(AbilityJson.read(BLAST).contains("\"indicator\""), "Blast names an indicator");
            assertEquals(IndicatorShowing.HELD, AbilityJson.decode(BLAST).indicator());
        }

        @Test
        void anAbilityNamingSelectedReadsSelected() {
            String json = AbilityJson.read("ender_blink").replace("\"held\"", "\"selected\"");
            assertEquals(IndicatorShowing.SELECTED, AbilityJson.decodeText("ender_blink", json).indicator());
        }

        @Test
        void enderBlinkPreviewReadsTheRangeItsTeleportJumps() {
            List<Step> steps = AbilityJson.decode("ender_blink").behaviors();
            assertEquals(32.0, TeleportStep.lookRange(steps).orElseThrow());
        }
    }

    /**
     * Each goo type carries at most one self + brew ability: a second fails
     * the load naming both, and a type carrying none is answered for the load's
     * warning (decision every-type-ships-one-brew-ability).
     */
    @Nested
    class OneBrewPerType {

        private static AbilityDefinition brew(String name) {
            return new AbilityDefinition(Identifier.fromNamespaceAndPath(Goo.MODID, name), GooTypes.ROCK,
                    name, "", 0, 0, Delivery.of(DeliveryKind.SELF), List.of(), List.of(),
                    AbilityBadge.BREW, List.of());
        }

        private static Map<Identifier, AbilityDefinition> byId(AbilityDefinition... definitions) {
            return Stream.of(definitions).collect(Collectors.toMap(AbilityDefinition::id, def -> def));
        }

        @Test
        void secondBrewOfATypeFailsTheLoadNamingBoth() {
            Map<Identifier, AbilityDefinition> abilities = byId(brew("rock_stoneskin"), brew("rock_pebbleskin"));

            ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                    () -> AbilityLoader.typesLackingABrew(abilities, List.of(GooTypes.ROCK)));

            assertTrue(refusal.getMessage().contains("goo:rock_stoneskin")
                    && refusal.getMessage().contains("goo:rock_pebbleskin"), refusal.getMessage());
        }

        @Test
        void typeCarryingNoBrewIsNamedAndOneCarryingABrewIsNot() {
            Map<Identifier, AbilityDefinition> abilities = byId(brew("rock_stoneskin"));

            assertEquals(List.of(GooTypes.FROST),
                    AbilityLoader.typesLackingABrew(abilities, List.of(GooTypes.ROCK, GooTypes.FROST)));
        }

        @Test
        void shippedBlazeAndLeafEachCarryExactlyOneBrew() {
            Map<Identifier, AbilityDefinition> shipped = scanShipped(AbilityJson.files());

            List<ResourceKey<GooTypeDefinition>> lacking = AbilityLoader.typesLackingABrew(shipped, GooTypes.BUNDLED);

            assertFalse(lacking.contains(GooTypes.BLAZE) || lacking.contains(GooTypes.LEAF), lacking.toString());
            for (ResourceKey<GooTypeDefinition> type : List.of(GooTypes.BLAZE, GooTypes.LEAF)) {
                long brews = shipped.values().stream()
                        .filter(def -> def.gooType() == type && SelfEatRoute.eats(def.delivery(), def.badge()))
                        .count();
                assertEquals(1, brews, type.identifier().toString());
            }
        }
    }

    private static List<Object> afterimageTypes(List<Step> steps) {
        return steps.stream().filter(AfterimageStep.class::isInstance)
                .map(step -> (Object) ((AfterimageStep) step).goo()).toList();
    }

    /** A step and every step it holds, depth first. */
    private static Stream<Step> stepTree(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(AbilityLoaderTest::stepTree));
    }

    /**
     * A resource manager whose listing under the ability directory is the given files.
     */
    private static ResourceManager managerListing(List<Path> files) {
        PackResources pack = mock(PackResources.class);
        Map<Identifier, Resource> listing = new HashMap<>();
        for (Path file : files) {
            Identifier path = Identifier.fromNamespaceAndPath(Goo.MODID, DIRECTORY + "/" + file.getFileName());
            listing.put(path, new Resource(pack, () -> Files.newInputStream(file)));
        }
        ResourceManager manager = mock(ResourceManager.class);
        when(manager.listResources(eq(DIRECTORY), any())).thenReturn(listing);
        return manager;
    }
}
