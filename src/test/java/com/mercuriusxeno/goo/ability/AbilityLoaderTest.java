package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AfterimageStep;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.ability.program.AilmentOverlayStep;
import com.mercuriusxeno.goo.ability.program.GhostTrailStep;
import com.mercuriusxeno.goo.ability.program.LingerStep;
import com.mercuriusxeno.goo.ability.program.PlaceBlockStep;
import com.mercuriusxeno.goo.ability.program.PotionStep;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.ability.program.TeleportStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.data.IdentifiedJsonScan;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    private static final Identifier GLOWING = Identifier.parse("minecraft:glowing");
    /** The abilities whose whole design was a per-stack shape. */
    /** The world abilities that stay after their blob lands. */
    private static final List<String> LINGERING_ABILITIES = List.of("crystal_cloud", "metal_spikes",
            "nether_black_hole", "unstable_proximity_mine", "glow_crystal", "crystal_prism");
    /** The ability whose program ends the tick it lands. */
    private static final String BLAST = "unstable_explode";
    private static final List<String> STACK_SHAPE_ABILITIES = List.of("blaze_flat", "blaze_tunnel",
            "frost_flat", "frost_tunnel", "frost_sphere", "rock_flat", "rock_tunnel");

    private static Map<Identifier, AbilityDefinition> scanShipped(List<Path> files) {
        return IdentifiedJsonScan.scan(managerListing(files), FileToIdConverter.json(DIRECTORY), JsonOps.INSTANCE,
                AbilityDefinition::codecFor);
    }

    /** The items each gated ability requires, as the task's how maps the operator's lists. */
    private static final Map<String, List<String>> GATES = Map.ofEntries(
            Map.entry("ender_blink", List.of("ender_pearl")),
            Map.entry("ender_teleport", List.of("popped_chorus_fruit")),
            Map.entry("hex_charm", List.of("honey_bottle", "cake", "cookie")),
            Map.entry("unstable_explode", List.of("gunpowder")),
            Map.entry("unstable_proximity_mine", List.of("tnt")),
            Map.entry("glow_laser", List.of("spectral_arrow")),
            Map.entry("glow_crystal", List.of("glowstone")),
            Map.entry("crystal_cloud", List.of("glass", "sand")),
            Map.entry("crystal_flechettes", List.of("amethyst_shard")),
            Map.entry("crystal_prism", List.of("quartz")),
            Map.entry("blaze_spitfire", List.of("torchflower")),
            Map.entry("blaze_ignite", List.of("flint")),
            Map.entry("blaze_kindle", List.of("magma_cream")),
            Map.entry("leaf_barkskin", List.of("oak_log")),
            Map.entry("aeon_time_stop", List.of("clock")),
            Map.entry("leaf_vines", List.of("vine")),
            Map.entry("leaf_growth", List.of("bone_meal")),
            Map.entry("leaf_reap", List.of("wheat", "wheat_seeds")),
            Map.entry("leaf_bio", List.of("poisonous_potato")),
            Map.entry("typhoon_levitate", List.of("shulker_shell")),
            Map.entry("typhoon_propel", List.of("phantom_membrane")),
            Map.entry("rock_bore", List.of("stone", "cobblestone")),
            Map.entry("rock_crush", List.of("gravel", "sand")),
            Map.entry("rock_flatten", List.of("dirt")),
            Map.entry("rock_petrify", List.of("pointed_dripstone")),
            Map.entry("rock_stoneskin", List.of("deepslate")),
            Map.entry("pulse_short_circuit", List.of("redstone")),
            Map.entry("shroom_mycosis", List.of("nether_wart")),
            Map.entry("shroom_colonize", List.of("brown_mushroom", "red_mushroom")),
            Map.entry("shroom_fungal_shift", List.of("sculk")),
            Map.entry("shroom_sight", List.of("sculk_sensor")));

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

    // decision splat-runs-the-program-no-fuse
    @Test
    void stackShapeAbilitiesLeaveTheRegistry() {
        Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());

        assertFalse(scanned.isEmpty(), "No ability scanned");
        for (String name : STACK_SHAPE_ABILITIES) {
            assertFalse(scanned.containsKey(Identifier.fromNamespaceAndPath(Goo.MODID, name)), name + " still loads");
        }
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
    @Test
    void eachLingeringAbilityPlacesItsOwnThingAndBlastPlacesNothing() {
        Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());
        for (String name : LINGERING_ABILITIES) {
            AbilityDefinition ability = scanned.get(Identifier.fromNamespaceAndPath(Goo.MODID, name));
            assertTrue(placesItsOwnThing(ability), name + " names no step placing its own thing");
        }
        AbilityDefinition blast = scanned.get(Identifier.fromNamespaceAndPath(Goo.MODID, BLAST));
        assertFalse(placesItsOwnThing(blast), BLAST + " places a thing of its own");
    }

    private static boolean placesItsOwnThing(AbilityDefinition ability) {
        return ability.behaviors().stream().flatMap(AbilityLoaderTest::withDescendants)
                .anyMatch(step -> step instanceof LingerStep || step instanceof PlaceBlockStep);
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(AbilityLoaderTest::withDescendants));
    }

    /**
     * Each shipped ability names the items the operator's lists gate it behind,
     * and an ungated one names none (decision ability-hidden-until-recipes-known).
     */
    @Test
    void everyShippedAbilityRequiresTheItemsItsGateNames() {
        List<Path> files = AbilityJson.files();
        Map<Identifier, AbilityDefinition> scanned = IdentifiedJsonScan.scan(
                managerListing(files), FileToIdConverter.json(DIRECTORY), JsonOps.INSTANCE,
                AbilityDefinition::codecFor);

        for (Path file : files) {
            Identifier fileId = AbilityJson.idOf(file.getFileName().toString());
            List<Identifier> expected = GATES.getOrDefault(fileId.getPath(), List.of()).stream()
                    .map(Identifier::withDefaultNamespace).toList();
            assertEquals(expected, scanned.get(fileId).requires(), fileId.toString());
        }
    }

    /**
     * Crystal's Prism names the nether quartz it consumes beside its goo
     * cost, and an ability naming no reagent consumes nothing
     * (decision ability-json-names-its-reagent).
     */
    @Test
    void prismConsumesANetherQuartzAndAnUnnamedReagentIsNone() {
        Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());

        assertEquals(List.of(Identifier.withDefaultNamespace("quartz")),
                scanned.get(Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_prism")).consumes());
        assertEquals(List.of(), scanned.get(Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_cloud")).consumes());
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
                assertEquals(1, ability.upkeep(), ability.id().toString());
            } else {
                assertEquals(AbilityDefinition.NO_UPKEEP, ability.upkeep(), ability.id().toString());
            }
        }
    }

    /**
     * Stoneskin's up sound is the petrify sound Statues plays, at another
     * pitch, and Nourish's is a healing cue of its own
     * (decision held-effects-sound-up-and-down).
     */
    @Test
    void heldEffectsSoundTheirOwnUpCue() {
        SoundStep stoneskinUp = firstSound("rock_stoneskin");
        assertEquals(Identifier.withDefaultNamespace("block.deepslate.place"), stoneskinUp.sound());
        assertNotEquals(1.0, stoneskinUp.pitch().evaluate(Variables.NONE), 1e-6);
        assertNotEquals(0.8, stoneskinUp.pitch().evaluate(Variables.NONE), 1e-6);
        assertTrue(AbilityJson.decode("vital_nourish").behaviors().stream().anyMatch(SoundStep.class::isInstance));
    }

    private static SoundStep firstSound(String name) {
        return AbilityJson.decode(name).behaviors().stream().filter(SoundStep.class::isInstance)
                .map(SoundStep.class::cast).findFirst().orElseThrow();
    }

    /**
     * Hex charm and aeon's stasis show their ailment through the overlay
     * step, and neither applies vanilla glowing any more
     * (decision ailment-overlay-shader-per-ailment).
     */
    @ParameterizedTest
    @CsvSource({"hex_charm, HEX", "aeon_time_stop, STASIS"})
    void ailmentAbilitiesWearTheOverlayInPlaceOfGlowing(String name, AilmentKind kind) {
        List<Step> steps = AbilityJson.decode(name).behaviors().stream()
                .flatMap(AbilityLoaderTest::stepTree).toList();

        assertEquals(List.of(kind), steps.stream().filter(AilmentOverlayStep.class::isInstance)
                .map(step -> ((AilmentOverlayStep) step).kind()).toList(), name);
        assertTrue(steps.stream().filter(PotionStep.class::isInstance)
                .noneMatch(step -> GLOWING.equals(((PotionStep) step).effect())), name + " still applies glowing");
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
            assertEquals(8.0, TeleportStep.lookRange(steps).orElseThrow());
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
