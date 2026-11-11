package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.held.LaidState;
import com.mercuriusxeno.goo.ability.program.AfterimageStep;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.ability.program.AilmentOverlayStep;
import com.mercuriusxeno.goo.ability.program.AirbornStep;
import com.mercuriusxeno.goo.ability.program.CoatStep;
import com.mercuriusxeno.goo.ability.program.BranchStep;
import com.mercuriusxeno.goo.ability.program.ChannelHost;
import com.mercuriusxeno.goo.ability.program.CharmStep;
import com.mercuriusxeno.goo.ability.program.CoolLavaStep;
import com.mercuriusxeno.goo.ability.program.EntityFilter;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.FloatStep;
import com.mercuriusxeno.goo.ability.program.GhostTrailStep;
import com.mercuriusxeno.goo.ability.program.HostVariables;
import com.mercuriusxeno.goo.ability.program.LingerStep;
import com.mercuriusxeno.goo.ability.program.PlaceBlockStep;
import com.mercuriusxeno.goo.ability.program.PotionStep;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.ability.program.TargetStep;
import com.mercuriusxeno.goo.ability.program.TeleportStep;
import com.mercuriusxeno.goo.ability.program.TomeKind;
import com.mercuriusxeno.goo.ability.program.TomeStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.ability.program.WobbleStep;
import com.mercuriusxeno.goo.data.GooValueTable;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final int CHARM_ROLLS = 1000;
    private static final int RARE_CHARMS = 50;
    private static final double ZOMBIE_MAX_HEALTH = 20;
    private static final double TOP_MAX_HEALTH = 1024;
    /** The one brew that starts silent (decision rally-drops-its-sound-and-gets-its-sprite). */
    private static final Identifier SILENT_BREW = Identifier.fromNamespaceAndPath(Goo.MODID, "zoo_rally");
    /** The proving example of a world ability that stays after its blob lands. */
    private static final String LINGERING = "crystal_cloud";
    /** The ability whose program ends the tick it lands. */
    private static final String BLAST = "unstable_explode";
    /** The abilities whose whole design was a per-stack shape. */
    private static final List<String> STACK_SHAPE_ABILITIES = List.of("frost_flat", "frost_tunnel", "frost_sphere", "rock_flat", "rock_tunnel");

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

    /**
     * Typhoon's abilities after the flight merge: Shulk levitates the struck
     * mob, and Float is typhoon's brew carrying the flight.
     * decision mob-float-renamed-shulk
     * decision tailwind-and-airborn-merge-into-float
     */
    @Nested
    class TyphoonFlight {

        private static final Set<String> TYPHOON_ABILITIES =
                Set.of("typhoon_shulk", "typhoon_float", "typhoon_gust", "typhoon_updraft", "typhoon_pneuma");

        @Test
        void typhoonCarriesShulkFloatGustUpdraftAndPneuma() {
            Set<String> typhoon = scanShipped(AbilityJson.files()).values().stream()
                    .filter(def -> def.gooType() == GooTypes.TYPHOON)
                    .map(def -> def.id().getPath()).collect(Collectors.toSet());

            assertEquals(TYPHOON_ABILITIES, typhoon);
        }

        @Test
        void shulkLevitatesTheStruckMob() {
            AbilityDefinition shulk = AbilityJson.decode("typhoon_shulk");

            assertEquals("goo.ability.typhoon.shulk", shulk.displayName());
            assertEquals(AbilityBadge.MOB, shulk.badge());
            assertTrue(shulk.behaviors().stream().anyMatch(FloatStep.class::isInstance), "no float step");
        }

        @Test
        void floatIsTyphoonsBrewCarryingTheFlight() {
            AbilityDefinition flight = AbilityJson.decode("typhoon_float");

            assertEquals("goo.ability.typhoon.float", flight.displayName());
            assertTrue(SelfEatRoute.eats(flight.delivery(), flight.badge()), "typhoon_float is not a durative");
            assertTrue(flight.behaviors().stream().anyMatch(AirbornStep.class::isInstance), "no airborn step");
            assertTrue(LaidState.laidBy(flight.behaviors()).contains(LaidState.AIRBORN),
                    "Float lays no flight to end with its held effect");
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

    // decision wither-ability-deleted
    @Test
    void netherHoldsNoWitherAbility() {
        Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());

        assertTrue(scanned.values().stream().anyMatch(def -> def.gooType() == GooTypes.NETHER), "No nether ability scanned");
        assertTrue(scanned.values().stream().filter(def -> def.gooType() == GooTypes.NETHER)
                .noneMatch(def -> def.id().getPath().contains("wither")), "a nether wither ability still loads");
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
     * Every shipped durative ability names an upkeep in place of a one-shot
     * cost, and every other ability names no upkeep
     * (decision self-effects-trickle-until-ended).
     */
    @Test
    void selfBrewAbilitiesNameAnUpkeepInPlaceOfACost() {
        Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());

        for (AbilityDefinition ability : scanned.values()) {
            if (SelfEatRoute.eats(ability.delivery(), ability.badge())) {
                assertEquals(0, ability.cost().in(GooValueTable.EMPTY), ability.id().toString());
                assertTrue(ability.upkeep().in(GooValueTable.EMPTY) >= 1, ability.id().toString());
            } else {
                assertEquals(AbilityDefinition.NO_UPKEEP, ability.upkeep().in(GooValueTable.EMPTY), ability.id().toString());
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
     * Every brew but Rally sounds as its effect starts, as every held effect
     * sounds as it ends.
     * held-effects-sound-up-and-down
     * rally-drops-its-sound-and-gets-its-sprite
     */
    @ParameterizedTest
    @MethodSource("shippedBrews")
    void everyBrewButRallySoundsAsItStarts(AbilityDefinition brew) {
        boolean sounds = brew.behaviors().stream().flatMap(AbilityLoaderTest::stepTree)
                .anyMatch(SoundStep.class::isInstance);
        assertEquals(!brew.id().equals(SILENT_BREW), sounds, brew.id().toString());
    }

    /** Every shipped durative ability (decision tests-read-the-inventory-from-loaded-json). */
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
     * An ability's description translation key: the one its JSON names, or its
     * displayName key plus ".description" when it names none.
     * decision ability-json-carries-a-description
     */
    @Nested
    class Description {

        private static final String SPIRE = "rock_spire";

        @Test
        void jsonNamingADescriptionReadsThatKey() {
            String json = AbilityJson.read(SPIRE).replaceFirst("\\{",
                    "{ \"description\": \"goo.ability.rock.spire.lifted\",");

            assertEquals("goo.ability.rock.spire.lifted", AbilityJson.decodeText(SPIRE, json).description());
        }

        @Test
        void jsonNamingNoDescriptionReadsItsDisplayNamePlusDescription() {
            assertFalse(AbilityJson.read(SPIRE).contains("\"description\""), "spire names a description");

            assertEquals("goo.ability.rock.spire.description", AbilityJson.decode(SPIRE).description());
        }
    }

    /**
     * Each dynamically priced ability carries the short formula its petal reads, and a zero-cost
     * ability naming cost_mechanics and paying no upkeep must name one.
     * decision dynamic-costs-keep-the-mb-quantity
     */
    @Nested
    class CostFormula {

        private static final Map<String, String> SHIPPED_FORMULAS = Map.of(
                "ender_blink", "200 + 100/block",
                "nether_black_hole", "1K x r\u00B3",
                "unstable_explode", "1K x r\u00B3",
                "rock_spire", "%s/m³",
                "rock_terrakinesis", "%s",
                "yore_age", "age/block");

        @Test
        void eachDynamicAbilityCarriesItsFormula() {
            SHIPPED_FORMULAS.forEach((name, formula) ->
                    assertEquals(Optional.of(formula), AbilityJson.decode(name).costFormula(), name));
        }

        @Test
        void zeroCostAbilityNamingCostMechanicsAndNoFormulaFailsNamingTheField() {
            String json = AbilityJson.read("rock_terrakinesis")
                    .replaceAll("\\s*\"cost_formula\"\\s*:\\s*\"[^\"]*\",?", "");

            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    () -> AbilityJson.decodeText("rock_terrakinesis", json));

            assertTrue(refusal.getMessage().contains("cost_formula"), refusal.getMessage());
        }
    }

    /**
     * An ability costing nothing up front names a cost_mechanics key explaining how it
     * charges; one with a plain cost may leave it out.
     * decision zero-cost-field-formats-the-cost-mechanics
     */
    @Nested
    class CostMechanics {

        private static final String TERRAKINESIS = "rock_terrakinesis";
        private static final String TERRAKINESIS_KEY = "goo.ability.rock.terrakinesis.cost_mechanics";
        private static final String NAMED_KEY = "\"cost_mechanics\": \"" + TERRAKINESIS_KEY + "\",";

        private static String terrakinesisNamingNoCostMechanics() {
            return AbilityJson.read(TERRAKINESIS).replaceAll("\\s*\"cost_mechanics\"\\s*:\\s*\"[^\"]*\",?", "");
        }

        @Test
        void zeroCostAbilityNamingNoCostMechanicsFailsNamingTheField() {
            String json = terrakinesisNamingNoCostMechanics();

            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    () -> AbilityJson.decodeText(TERRAKINESIS, json));

            assertTrue(refusal.getMessage().contains("cost_mechanics"), refusal.getMessage());
        }

        @Test
        void absentCostNamingNoCostMechanicsFailsNamingTheField() {
            String json = terrakinesisNamingNoCostMechanics().replaceAll("\"cost\"\\s*:\\s*0,", "");

            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    () -> AbilityJson.decodeText(TERRAKINESIS, json));

            assertTrue(refusal.getMessage().contains("cost_mechanics"), refusal.getMessage());
        }

        @Test
        void zeroCostAbilityNamingCostMechanicsLoadsWithThatKey() {
            String json = terrakinesisNamingNoCostMechanics().replaceFirst("\\{", "{ " + NAMED_KEY);

            assertEquals(Optional.of(TERRAKINESIS_KEY), AbilityJson.decodeText(TERRAKINESIS, json).costMechanics());
        }

        @Test
        void plainCostAbilityLoadsNamingNoCostMechanics() {
            assertFalse(AbilityJson.read(BLAST).contains("cost_mechanics"), "Blast names cost_mechanics");

            AbilityDefinition blast = AbilityJson.decode(BLAST);

            assertTrue(blast.cost().in(GooValueTable.EMPTY) > 0, "Blast's cost " + blast.cost().in(GooValueTable.EMPTY));
            assertEquals(Optional.empty(), blast.costMechanics());
        }
    }

    /**
     * Each goo type carries at most one durative ability: a second fails
     * the load naming both, and a type carrying none is answered for the load's
     * warning (decision every-type-ships-one-brew-ability).
     */
    @Nested
    class OneBrewPerType {

        private static AbilityDefinition brew(String name) {
            return new AbilityDefinition(Identifier.fromNamespaceAndPath(Goo.MODID, name), GooTypes.ROCK,
                    name, "", 0, 0, Delivery.of(DeliveryKind.SELF), List.of(), List.of(),
                    AbilityBadge.DURATIVE, List.of());
        }

        private static Map<Identifier, AbilityDefinition> byId(AbilityDefinition... definitions) {
            return Stream.of(definitions).collect(Collectors.toMap(AbilityDefinition::id, def -> def));
        }

        @Test
        void secondBrewOfATypeFailsTheLoadNamingBoth() {
            Map<Identifier, AbilityDefinition> abilities = byId(brew("rock_stoneskin"), brew("rock_pebbleskin"));

            ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                    () -> AbilityLoader.typesLackingADurative(abilities, List.of(GooTypes.ROCK)));

            assertTrue(refusal.getMessage().contains("goo:rock_stoneskin")
                    && refusal.getMessage().contains("goo:rock_pebbleskin"), refusal.getMessage());
        }

        @Test
        void typeCarryingNoBrewIsNamedAndOneCarryingABrewIsNot() {
            Map<Identifier, AbilityDefinition> abilities = byId(brew("rock_stoneskin"));

            assertEquals(List.of(GooTypes.FROST),
                    AbilityLoader.typesLackingADurative(abilities, List.of(GooTypes.ROCK, GooTypes.FROST)));
        }

        @Test
        void shippedBlazeLeafAndZooEachCarryExactlyOneBrew() {
            Map<Identifier, AbilityDefinition> shipped = scanShipped(AbilityJson.files());
            List<ResourceKey<GooTypeDefinition>> carriers = List.of(GooTypes.BLAZE, GooTypes.LEAF, GooTypes.ZOO);

            List<ResourceKey<GooTypeDefinition>> lacking = AbilityLoader.typesLackingADurative(shipped, GooTypes.BUNDLED);

            assertTrue(carriers.stream().noneMatch(lacking::contains), lacking.toString());
            for (ResourceKey<GooTypeDefinition> type : carriers) {
                long brews = shipped.values().stream()
                        .filter(def -> def.gooType() == type && SelfEatRoute.eats(def.delivery(), def.badge()))
                        .count();
                assertEquals(1, brews, type.identifier().toString());
            }
        }

        /** decision zoo-rally-is-a-brew-ability */
        @Test
        void shippedZooBrewIsRally() {
            List<Identifier> zooBrews = scanShipped(AbilityJson.files()).values().stream()
                    .filter(def -> def.gooType() == GooTypes.ZOO && SelfEatRoute.eats(def.delivery(), def.badge()))
                    .map(AbilityDefinition::id)
                    .toList();

            assertEquals(List.of(Identifier.fromNamespaceAndPath(Goo.MODID, "zoo_rally")), zooBrews);
        }

        /** decision zoo-rally-is-a-brew-ability */
        @Test
        void shippedZooRallyEatsAsABrewPayingUpkeep() {
            AbilityDefinition rally = AbilityJson.decode("zoo_rally");

            assertEquals(AbilityBadge.DURATIVE, rally.badge());
            assertEquals(DeliveryKind.SELF, rally.delivery().kind());
            assertTrue(rally.upkeep().in(GooValueTable.EMPTY) > 0, "upkeep " + rally.upkeep().in(GooValueTable.EMPTY));
            assertEquals(0, rally.cost().in(GooValueTable.EMPTY));
            assertTrue(SelfEatRoute.eats(rally.delivery(), rally.badge()));
        }

        /** decision zoo-rally-holds-for-the-brew */
        @Test
        void shippedZooRallyLaysTheRallyItsEndingClears() {
            AbilityDefinition rally = AbilityJson.decode("zoo_rally");

            assertTrue(LaidState.laidBy(rally.behaviors()).contains(LaidState.RALLY),
                    LaidState.laidBy(rally.behaviors()).toString());
        }
    }

    /**
     * Weird's abilities are the make-this-harmless family: Goop's stream coats
     * what it hits, Bounce stays the long-range pad, and Slime stands apart.
     * weird-bounces-and-softens-harm
     */
    @Nested
    class WeirdHarmlessFamily {

        @Test
        void weirdLoadsBounceGoopAndSlimeAlone() {
            Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());

            Set<String> weird = scanned.values().stream().filter(def -> def.gooType() == GooTypes.WEIRD)
                    .map(def -> def.id().getPath()).collect(Collectors.toSet());

            assertEquals(Set.of("weird_bounce", "weird_goop", "weird_slime"), weird);
            assertFalse(scanned.containsKey(Identifier.fromNamespaceAndPath(Goo.MODID, "weird_wobble")),
                    "weird_wobble still loads");
            assertFalse(scanned.containsKey(Identifier.fromNamespaceAndPath(Goo.MODID, "weird_magma")),
                    "weird_magma still loads");
        }

        /** decision coat-is-renamed-goop */
        @Test
        void goopShipsUnderWeirdNamedGoopWithItsIcon() throws IOException {
            assertWeirdPetalShipsAs("weird_goop", "Goop");
        }

        @Test
        void goopStreamsCoolingLavaAndCoatingBlocksInTheBlockPass() {
            AbilityDefinition goop = AbilityJson.decode("weird_goop");
            List<Step> blockPass = ChannelHost.passSteps(goop.behaviors(), true);

            assertEquals(DeliveryKind.STREAM, goop.delivery().kind());
            // coat-is-a-green-slime-film-over-the-blocks-own-texture
            assertEquals(List.of(CoolLavaStep.class, CoatStep.class), blockPass.stream().map(Object::getClass).toList());
            assertTrue(ProgramBehavior.serves(blockPass, HostKind.PLAYER), "the channeling player runs the block pass");
        }

        @Test
        void goopWobblesTheMobItHitsUnderTheWobbleOverlay() {
            List<Step> entityPass = ChannelHost.passSteps(AbilityJson.decode("weird_goop").behaviors(), false);

            TargetStep onMob = entityPass.stream().filter(TargetStep.class::isInstance).map(TargetStep.class::cast)
                    .findFirst().orElseThrow();
            assertEquals(List.of(EntityFilter.MOB), onMob.where());
            assertTrue(onMob.steps().stream().anyMatch(WobbleStep.class::isInstance), "no wobble step on the mob");
            assertTrue(onMob.steps().stream().anyMatch(step -> step instanceof AilmentOverlayStep overlay
                    && overlay.kind() == AilmentKind.WOBBLE), "no wobble overlay on the mob");
            assertTrue(ProgramBehavior.serves(entityPass, HostKind.ENTITY), "the struck entity runs the entity pass");
        }

        @Test
        void bounceDumpsFourCoatLayersWhereItsArcLands() {
            AbilityDefinition bounce = AbilityJson.decode("weird_bounce");

            assertEquals(DeliveryKind.ARC, bounce.delivery().kind());
            // bounce-throw-dumps-layers-that-flow-out: Bounce's blob lays the coat Goop lays, k layers at once
            assertEquals(List.of(CoatStep.splatted(4)), bounce.behaviors());
        }
    }

    /**
     * Asserts a shipped ability stands under Weird, its lang key reads the given name, and its
     * icon resolves to the convention path and ships on the classpath.
     */
    private static void assertWeirdPetalShipsAs(String path, String name) throws IOException {
        Identifier id = Identifier.fromNamespaceAndPath(Goo.MODID, path);
        AbilityDefinition petal = scanShipped(AbilityJson.files()).get(id);
        assertNotNull(petal, id + " ships");
        assertEquals(GooTypes.WEIRD, petal.gooType());
        assertEquals(name, shippedLang().get(petal.displayName()).getAsString(), petal.displayName());
        Identifier icon = AbilityIconTexture.of(id, petal.icon());
        assertEquals(Identifier.fromNamespaceAndPath(Goo.MODID, "textures/goo/ability/" + path + ".png"), icon);
        try (InputStream in = AbilityLoaderTest.class.getResourceAsStream("/assets/goo/" + icon.getPath())) {
            assertNotNull(in, icon + " ships");
        }
    }

    private static JsonObject shippedLang() throws IOException {
        try (InputStream in = AbilityLoaderTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
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
