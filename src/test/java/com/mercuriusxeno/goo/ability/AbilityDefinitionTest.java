package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.DamageStep;
import com.mercuriusxeno.goo.ability.program.ExplodeStep;
import com.mercuriusxeno.goo.ability.program.ExplosionMarch;
import com.mercuriusxeno.goo.ability.program.ExplosionMode;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.data.PrefabValues;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the area an ability's definition draws while right click is held: an
 * explosive ability's sphere is sized to its explosion's max reach, and an instant
 * area throw that wrote no area draws the sphere its program reaches.
 */
class AbilityDefinitionTest {

    private static final double JSON_SIZE = 2.5;
    private static final double TOLERANCE = 1e-9;
    private static final double CONE_ANGLE = 30;
    private static final double EXPLOSION_POWER = 3;
    /** The explosion power Lurker's JSON writes. */
    private static final float LURKER_POWER = 2.5f;
    /** The widest radius Razor's field step writes. */
    private static final double RAZOR_RADIUS = 4.5;
    /** The damage each knife Knives slings deals. */
    private static final double KNIFE_DAMAGE = 2;
    private static final Pattern RADIUS_BEARING = Pattern.compile("\"radius\"|\"type\"\s*:\s*\"explode\"");

    // decision one-proving-ability-per-kind
    @Test
    void explosiveSphereIsDrawnAtTheExplosionsMaxReach() {
        assertEquals(ExplosionMarch.maxReach(LURKER_POWER), AbilityJson.decode("unstable_lurker").area().size(),
                TOLERANCE);
    }

    @Test
    void sphereOverAProgramThatNeverExplodesKeepsItsSize() {
        AbilityArea written = new AbilityArea(AbilityArea.Shape.SPHERE, JSON_SIZE, 0);
        assertEquals(written, AbilityDefinition.previewAtMaxReach(written, List.of()));
    }

    @Test
    void coneOverAnExplosionKeepsItsSize() {
        AbilityArea written = new AbilityArea(AbilityArea.Shape.CONE, JSON_SIZE, CONE_ANGLE);
        List<Step> explodes = List.of(new ExplodeStep(Expr.literal(EXPLOSION_POWER), ExplosionMode.TNT));
        assertEquals(written, AbilityDefinition.previewAtMaxReach(written, explodes));
    }

    // decision knives-deal-two-each
    @Test
    void knivesDealsTwoAKnife() {
        DamageStep damage = AbilityJson.decode("crystal_knives").behaviors().stream()
                .filter(DamageStep.class::isInstance).map(DamageStep.class::cast).findFirst().orElseThrow();
        assertEquals(KNIFE_DAMAGE, damage.amount().evaluate(Variables.NONE), TOLERANCE);
    }

    @Test
    void spawnConsumesNoItemAndNeedsNoRecipe() {
        // ability-json-names-its-reagent
        // spawn-hostile-shape-peaceful-from-a-slime
        AbilityDefinition spawn = AbilityJson.decode("zoo_spawn");
        assertEquals(List.of(), spawn.consumes());
        assertEquals(List.of(), spawn.requires());
    }

    /**
     * A cost and an upkeep decode written as a number and as an expression alike, an item in the
     * expression reading its value in the ability's own goo type
     * (decision ability-cost-builds-as-a-one-type-by-type-value).
     */
    @Nested
    class Costs {

        private static final Identifier GLOWSTONE = Identifier.withDefaultNamespace("glowstone_dust");
        private static final String MUNDANE_BLOCK = "mundane_block";
        private static final String SHIPPED_BASE_VALUES = "/data/goo/goo_values/base_values.json";

        private static AbilityDefinition costing(String cost, String upkeep) {
            return AbilityJson.decodeText("made_up", "{\"gooType\": \"rock\", \"displayName\": \"made_up\","
                    + " \"cost\": " + cost + ", \"upkeep\": " + upkeep + ", \"badge\": \"world\", \"behaviors\": [],"
                    + " \"delivery\": {\"kind\": \"arc\"}}");
        }

        private static IGooValueLookup rockGlowstoneAt(int mb) {
            IGooValueLookup values = mock(IGooValueLookup.class);
            when(values.lookup(GLOWSTONE)).thenReturn(new GooValue(Map.of(GooTypes.ROCK, mb)));
            return values;
        }

        @Test
        void aNumberCostAndUpkeepEvaluateToThemselves() {
            AbilityDefinition flat = costing("1000", "4");

            assertEquals(1000, flat.cost().in(GooValueTable.EMPTY));
            assertEquals(4, flat.upkeep().in(GooValueTable.EMPTY));
        }

        @Test
        void anExpressionCostAndUpkeepEvaluateOverTheItemsTheyName() {
            AbilityDefinition priced = costing("\"2 * minecraft:glowstone_dust + 100\"",
                    "\"minecraft:glowstone_dust / 50\"");
            IGooValueLookup values = rockGlowstoneAt(200);

            assertEquals(500, priced.cost().in(values));
            assertEquals(4, priced.upkeep().in(values));
        }

        @Test
        void aCostNamingPrefabsReadsAScalarWholeAndATreeAtTheAbilitysType() {
            AbilityDefinition priced = costing("\"2 * $base + $stone\"", "0");
            IGooValueLookup values = mock(IGooValueLookup.class);
            when(values.prefabs()).thenReturn(new PrefabValues(Map.of("base", GooFraction.of(300, 1)),
                    Map.of("stone", new GooValue(Map.of(GooTypes.ROCK, 250, GooTypes.LEAF, 9)))));

            assertEquals(850, priced.cost().in(values));
        }

        @Test
        void aDecimalCostAndAFractionUpkeepPriceExactly() {
            AbilityDefinition fractional = costing("12.5", "\"1 / 2\"");

            assertEquals(GooFraction.of(25, 2), fractional.cost().exactIn(GooValueTable.EMPTY));
            assertEquals(GooFraction.of(1, 2), fractional.upkeep().exactIn(GooValueTable.EMPTY));
        }

        @Test
        void aCostOverFractionalPrefabsSumsExactly() {
            AbilityDefinition priced = costing("\"3 * $third + $third / 2\"", "0");
            IGooValueLookup values = mock(IGooValueLookup.class);
            when(values.prefabs()).thenReturn(new PrefabValues(Map.of("third", GooFraction.of(1, 3)), Map.of()));

            assertEquals(GooFraction.of(7, 6), priced.cost().exactIn(values));
        }

        /**
         * Spire prices each cubic meter of stone it grows at $mundane_block, read in rock from the shipped values.
         * decision spire-cost-field-prices-each-cubic-meter-of-stone-at-mundane-block
         */
        @Test
        void spireCostReadsMundaneBlockInRockFromTheShippedValues() throws IOException {
            AbilityPrice spireCost = AbilityJson.decode("rock_spire").cost();
            IGooValueLookup shipped = GooBaseValuesPreview.registry(shippedBaseValues()).table();
            GooFraction mundaneBlock = shipped.prefabs().in(MUNDANE_BLOCK, GooTypes.ROCK);

            assertEquals("$" + MUNDANE_BLOCK, spireCost.source());
            assertTrue(mundaneBlock != null && !mundaneBlock.isZero(), "$mundane_block prices no rock");
            assertEquals(mundaneBlock, spireCost.exactIn(shipped));
        }

        private static String shippedBaseValues() throws IOException {
            try (InputStream stream = AbilityDefinitionTest.class.getResourceAsStream(SHIPPED_BASE_VALUES)) {
                assertNotNull(stream, SHIPPED_BASE_VALUES);
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * The arm pose an ability's JSON names, or its delivery kind's default where
     * it names none (decision ability-json-names-its-arm-pose).
     */
    @Nested
    class ArmPoses {

        private static AbilityDefinition decoded(String delivery, String poseField) {
            return AbilityJson.decodeText("made_up", "{\"gooType\": \"rock\", \"displayName\": \"made_up\","
                    + " \"cost\": 10, \"badge\": \"world\", \"behaviors\": [],"
                    + " \"delivery\": {\"kind\": \"" + delivery + "\"}" + poseField + "}");
        }

        @ParameterizedTest
        @CsvSource({"arc, SWUNG", "beam, HELD_OUT", "stream, HELD_OUT", "self, HELD_UP"})
        void anAbilityNamingNoPoseTakesItsDeliveryKindsDefault(String delivery, ArmPose expected) {
            assertEquals(expected, decoded(delivery, "").pose());
        }

        @ParameterizedTest
        @EnumSource(ArmPose.class)
        void anAbilityNamingAPoseTakesIt(ArmPose named) {
            String field = ", \"pose\": \"" + named.name().toLowerCase(Locale.ROOT) + "\"";
            assertEquals(named, decoded("self", field).pose());
        }

        @Test
        void onlyTheSwungArcDefaultSwingsOnRelease() {
            assertEquals(ArmPoseKind.NONE, ArmPose.SWUNG.holdFrame());
            assertEquals(ArmPoseKind.NONE, ArmPose.SWUNG.releaseFrame());
            assertTrue(ArmPose.SWUNG.swingsOnRelease());
            assertFalse(ArmPose.HELD_OUT.swingsOnRelease());
            assertFalse(ArmPose.HELD_UP.swingsOnRelease());
            assertFalse(ArmPose.SHOULDER_CAST.swingsOnRelease());
            assertEquals(ArmPoseKind.NONE, ArmPose.HELD_OUT.releaseFrame());
            assertEquals(ArmPoseKind.HOLD_UP, ArmPose.HELD_UP.holdFrame());
        }

        @Test
        void knivesCastsFromTheShoulder() {
            AbilityDefinition knives = AbilityJson.decode("crystal_knives");
            assertEquals(ArmPose.SHOULDER_CAST, knives.pose());
            assertEquals(ArmPoseKind.WIND_UP, knives.pose().holdFrame());
            assertEquals(ArmPoseKind.FLING, knives.pose().releaseFrame());
        }

        // crystal-shards-becomes-crystal-knives-everywhere
        @Test
        void knivesDecodesAsTheChanneledCrystalAbilityNamedKnives() {
            AbilityDefinition knives = AbilityJson.decode("crystal_knives");
            assertEquals("goo.ability.crystal.knives", knives.displayName());
            assertEquals(AbilityBadge.CHANNELED, knives.badge());
        }
    }

    /** An instant area throw writing no area draws the sphere its program reaches. */
    @Nested
    class DerivedFromTheProgram {

        @Test
        void everyShippedInstantAreaThrowResolvesAnArea() {
            List<Path> throwsChecked = AbilityJson.files().stream().filter(DerivedFromTheProgram::isInstantAreaThrow)
                    .toList();
            assertFalse(throwsChecked.isEmpty());
            for (Path file : throwsChecked) {
                AbilityArea area = AbilityJson.decode(file).area();
                assertTrue(area.shape() != AbilityArea.Shape.NONE && area.size() > 0, file + " resolves " + area);
            }
        }

        // decision one-proving-ability-per-kind
        @Test
        void fieldThrowDrawsASphereAtItsWidestRadius() {
            assertEquals(new AbilityArea(AbilityArea.Shape.SPHERE, RAZOR_RADIUS, 0),
                    AbilityJson.decode("crystal_cloud").area());
        }

        @Test
        void throwWithNoRadiusDrawsNoArea() {
            assertEquals(AbilityArea.NONE, AbilityJson.decode("crystal_prism").area());
        }

        private static boolean isInstantAreaThrow(Path file) {
            String name = file.getFileName().toString().replace(".json", "");
            AbilityDefinition definition = AbilityJson.decode(file);
            boolean aimsTheWorld = definition.badge() == AbilityBadge.WORLD || definition.badge() == AbilityBadge.FREE;
            // black-hole-leaves-a-compression-sphere: a sized cast draws its dragged radius, not a thrown area
            return definition.delivery().kind() == DeliveryKind.ARC && aimsTheWorld
                    && !definition.hasTag(AbilityTags.DRAG_SIZED)
                    && RADIUS_BEARING.matcher(AbilityJson.read(name)).find();
        }
    }

    /** An area a JSON writes, and the area of every ability that is not an instant area throw, stands. */
    @Nested
    class WrittenOrNotAThrow {

        // decision one-proving-ability-per-kind
        @Test
        void writtenAreaStandsOverTheDerivedOne() {
            assertEquals(new AbilityArea(AbilityArea.Shape.LINE, 0, 0), AbilityJson.decode("glow_crystal").area());
        }

        /** One proving example per kind: a thrown channel, a self, a brew and a stream. */
        @ParameterizedTest
        @CsvSource({"crystal_knives, NONE, 0.0, 0.0", "ender_blink, NONE, 0.0, 0.0",
                "blaze_kindle, NONE, 0.0, 0.0", "blaze_flames, CONE, 6.0, 20.0"})
        void mobSelfBrewAndChanneledKeepTheirArea(String name, AbilityArea.Shape shape, double size, double angle) {
            assertEquals(new AbilityArea(shape, size, angle), AbilityJson.decode(name).area());
        }

        @Test
        void mobThrowOverAFieldStepDrawsNoArea() {
            List<Step> field = AbilityJson.decode("crystal_cloud").behaviors();
            assertEquals(AbilityArea.NONE,
                    AbilityDefinition.heldArea(AbilityArea.NONE, Delivery.ARC, AbilityBadge.MOB, field));
        }

        @Test
        void beamOverAFieldStepDrawsNoArea() {
            List<Step> field = AbilityJson.decode("crystal_cloud").behaviors();
            assertEquals(AbilityArea.NONE,
                    AbilityDefinition.heldArea(AbilityArea.NONE, Delivery.BEAM, AbilityBadge.WORLD, field));
        }

        @Test
        void freeThrowOverAFieldStepDrawsItsSphere() {
            List<Step> field = AbilityJson.decode("crystal_cloud").behaviors();
            assertEquals(new AbilityArea(AbilityArea.Shape.SPHERE, RAZOR_RADIUS, 0),
                    AbilityDefinition.heldArea(AbilityArea.NONE, Delivery.ARC, AbilityBadge.FREE, field));
        }
    }
}
