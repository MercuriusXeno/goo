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
import java.nio.file.Path;
import java.util.List;
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
    private static final Pattern RADIUS_BEARING = Pattern.compile("\"radius\"|\"type\"\s*:\s*\"explode\"");

    @ParameterizedTest
    @CsvSource({"unstable_explode, 3.0", "unstable_proximity_mine, 2.5", "unstable_timed_bomb, 2.0"})
    void explosiveSphereIsDrawnAtTheExplosionsMaxReach(String name, float power) {
        assertEquals(ExplosionMarch.maxReach(power), AbilityJson.decode(name).area().size(), TOLERANCE);
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

        @ParameterizedTest
        @CsvSource({"crystal_cloud, 4.5", "metal_spikes, 3.75", "rock_crush, 2.0",
                "shroom_colonize, 3.0", "weird_magma, 3.0"})
        void fieldThrowDrawsASphereAtItsWidestRadius(String name, double radius) {
            assertEquals(new AbilityArea(AbilityArea.Shape.SPHERE, radius, 0), AbilityJson.decode(name).area());
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

        @ParameterizedTest
        @CsvSource({"glow_crystal, LINE, 0.0, 0.0", "blaze_spitfire, CONE, 6.0, 20.0",
                "unstable_explode, SPHERE, 4.0, 0.0"})
        void writtenAreaStandsOverTheDerivedOne(String name, AbilityArea.Shape shape, double size, double angle) {
            assertEquals(new AbilityArea(shape, size, angle), AbilityJson.decode(name).area());
        }

        @ParameterizedTest
        @CsvSource({"crystal_flechettes, NONE, 0.0, 0.0", "ender_blink, NONE, 0.0, 0.0",
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
            assertEquals(new AbilityArea(AbilityArea.Shape.SPHERE, 4.5, 0),
                    AbilityDefinition.heldArea(AbilityArea.NONE, Delivery.ARC, AbilityBadge.FREE, field));
        }
    }
}
