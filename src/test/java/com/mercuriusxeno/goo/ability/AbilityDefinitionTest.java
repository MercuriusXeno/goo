package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.ExplodeStep;
import com.mercuriusxeno.goo.ability.program.ExplosionMarch;
import com.mercuriusxeno.goo.ability.program.ExplosionMode;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.Step;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void spawnConsumesNoItemAndNeedsNoRecipe() {
        // ability-json-names-its-reagent
        // spawn-hostile-shape-peaceful-from-a-slime
        AbilityDefinition spawn = AbilityJson.decode("zoo_spawn");
        assertEquals(List.of(), spawn.consumes());
        assertEquals(List.of(), spawn.requires());
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
        void shardsCastsFromTheShoulder() {
            AbilityDefinition shards = AbilityJson.decode("crystal_shards");
            assertEquals(ArmPose.SHOULDER_CAST, shards.pose());
            assertEquals(ArmPoseKind.WIND_UP, shards.pose().holdFrame());
            assertEquals(ArmPoseKind.FLING, shards.pose().releaseFrame());
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
        @CsvSource({"crystal_shards, NONE, 0.0, 0.0", "ender_blink, NONE, 0.0, 0.0",
                "blaze_kindle, NONE, 0.0, 0.0", "blaze_spitfire, CONE, 6.0, 20.0"})
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
