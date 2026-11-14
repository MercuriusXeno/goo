package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.AwaitEntityStep;
import com.mercuriusxeno.goo.ability.program.EntitiesStep;
import com.mercuriusxeno.goo.ability.program.EntityFilter;
import com.mercuriusxeno.goo.ability.program.ExplodeStep;
import com.mercuriusxeno.goo.ability.program.ExplosionMarch;
import com.mercuriusxeno.goo.ability.program.ExplosionMode;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.LingerStep;
import com.mercuriusxeno.goo.ability.program.SelectionShape;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.TargetStep;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.data.PrefabValues;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final double CONE_ANGLE = 30;
    private static final double EXPLOSION_POWER = 3;
    private static final double NARROW_RADIUS = 2;
    private static final double WIDE_RADIUS = 4;
    private static final Pattern RADIUS_BEARING = Pattern.compile("\"radius\"|\"type\"\s*:\s*\"explode\"");

    @Test
    void explosiveSphereIsDrawnAtTheExplosionsMaxReach() {
        AbilityArea written = new AbilityArea(AbilityArea.Shape.SPHERE, JSON_SIZE, 0);
        List<Step> explodes = List.of(new ExplodeStep(Expr.literal(EXPLOSION_POWER), ExplosionMode.TNT));
        assertEquals(new AbilityArea(AbilityArea.Shape.SPHERE, ExplosionMarch.maxReach((float) EXPLOSION_POWER), 0),
                AbilityDefinition.previewAtMaxReach(written, explodes));
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

    @Test
    void spawnConsumesNoItemAndNeedsNoRecipe() {
        // ability-json-names-its-reagent
        // spawn-hostile-shape-peaceful-from-a-slime
        AbilityDefinition spawn = AbilityJson.decode("zoo_spawn");
        assertEquals(List.of(), spawn.consumes());
        assertEquals(List.of(), spawn.requires());
    }

    // decision blast-keeps-its-explosion-gated-on-gunpowder
    @Test
    void blastRequiresGunpowder() {
        assertEquals(List.of(Identifier.withDefaultNamespace("gunpowder")),
                AbilityJson.decode("unstable_blast").requires());
    }

    // decision razor-keeps-its-look-gated-on-glass
    @Test
    void razorRequiresGlassAndSand() {
        assertEquals(List.of(Identifier.withDefaultNamespace("glass"), Identifier.withDefaultNamespace("sand")),
                AbilityJson.decode("crystal_cloud").requires());
    }

    /**
     * Expire strikes a mob that is no boss.
     */
    // decision expire-kills-the-struck-mob
    @Test
    void expireStrikesAMobThatIsNoBoss() {
        TargetStep target = (TargetStep) AbilityJson.decode("yore_expire").behaviors().getFirst();
        assertEquals(List.of(EntityFilter.MOB, EntityFilter.NOT_BOSS), target.where());
    }

    /**
     * Lurker's trigger keeps every living entity, so a player standing in its radius sets it off as a zombie does.
     */
    @Test
    void lurkersTriggerKeepsEveryLivingEntity() {
        AwaitEntityStep trigger = LingerStep.bodyOf(AbilityJson.decode("unstable_lurker").behaviors()).orElseThrow()
                .stream().filter(AwaitEntityStep.class::isInstance).map(AwaitEntityStep.class::cast)
                .findFirst().orElseThrow();
        assertEquals(List.of(EntityFilter.LIVING), trigger.where());
    }

    private static AbilityDefinition requiring(String requires) {
        return AbilityJson.decodeText("made_up", "{\"gooType\": \"rock\", \"displayName\": \"made_up\","
                + " \"cost\": 10, \"badge\": \"world\", \"behaviors\": [], \"delivery\": {\"kind\": \"arc\"},"
                + " \"requires\": " + requires + "}");
    }

    // decision ability-hidden-until-recipes-known
    @Test
    void anAbilityRequiringNothingIsKnownToAPlayerWhoKnowsNothing() {
        assertEquals(true, requiring("[]").isKnownTo(KnownItems.NONE));
    }

    // decision ability-hidden-until-recipes-known
    @Test
    void anAbilityRequiringAnItemThePlayerLacksIsHiddenFromThem() {
        assertEquals(false, requiring("[\"minecraft:gunpowder\"]").isKnownTo(KnownItems.NONE));
    }

    // decision ability-hidden-until-recipes-known
    @Test
    void anAbilityIsKnownToAPlayerWhoKnowsEveryItemItRequires() {
        assertEquals(true, requiring("[\"minecraft:gunpowder\"]")
                .isKnownTo(KnownItems.NONE.with(Identifier.withDefaultNamespace("gunpowder"))));
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
         * Spire prices each cubic meter of stone it grows at $mundane_block, read in rock, here from a fixture's
         * $mundane_block of 7 rock and 5 metal.
         * decision spire-cost-field-prices-each-cubic-meter-of-stone-at-mundane-block
         * decision no-test-asserts-a-shipped-base-value
         */
        @Test
        void spireCostReadsMundaneBlockInRock() {
            AbilityPrice spireCost = AbilityJson.decode("rock_spire").cost();
            IGooValueLookup fixture = GooBaseValuesPreview.registry("""
                    {"_constants": {"mundane_block": {"rock": 7, "metal": 5}}}
                    """).table();

            assertEquals("$" + MUNDANE_BLOCK, spireCost.source());
            assertEquals(GooFraction.of(7), spireCost.exactIn(fixture));
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

        @Test
        void fieldThrowDrawsASphereAtItsWidestRadius() {
            List<Step> radii = List.of(
                    new EntitiesStep(SelectionShape.SPHERE, Expr.literal(NARROW_RADIUS), List.of(), List.of()),
                    new EntitiesStep(SelectionShape.SPHERE, Expr.literal(WIDE_RADIUS), List.of(), List.of()));
            assertEquals(new AbilityArea(AbilityArea.Shape.SPHERE, WIDE_RADIUS, 0),
                    AbilityDefinition.heldArea(AbilityArea.NONE, Delivery.ARC, AbilityBadge.WORLD, radii));
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
            assertEquals(new AbilityArea(AbilityArea.Shape.LINE, 0, 0), AbilityJson.decode("glow_bulb").area());
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
            assertEquals(AbilityArea.Shape.SPHERE,
                    AbilityDefinition.heldArea(AbilityArea.NONE, Delivery.ARC, AbilityBadge.FREE, field).shape());
        }
    }

    @Test
    void laserLocksOnToTheAimedMob() {
        assertTrue(AbilityJson.decode("glow_laser").hasTag(AbilityTags.LOCKS_ON));
    }

    @Test
    void zapFliesAsABeam() {
        assertTrue(AbilityJson.decode("pulse_zap").delivery().fliesStraight());
    }
}
