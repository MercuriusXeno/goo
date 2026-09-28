package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityMath;
import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.ability.program.AreaShape;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FrostExplosionVisual's rolling freeze fog: its timing, the zone it fills,
 * and the shader pair its pipeline names.
 */
class FrostExplosionVisualTest {

    private static final float TOLERANCE = 1e-5f;
    private static final int MAX_FROST_STACKS = 4;

    private static float progressAt(float tick) {
        return tick / FrostExplosionVisual.DURATION_TICKS;
    }

    @Test
    void fogRollsOutOverTwentyTicksFastThenSlow() {
        assertEquals(0f, FrostExplosionVisual.rolled(0f), 0f);
        assertEquals(1f, FrostExplosionVisual.rolled(progressAt(FrostExplosionVisual.ROLL_TICKS)), TOLERANCE);
        assertEquals(1f, FrostExplosionVisual.rolled(1f), TOLERANCE);
        float quarter = FrostExplosionVisual.ROLL_TICKS / 4f;
        float early = FrostExplosionVisual.rolled(progressAt(quarter));
        float late = 1f - FrostExplosionVisual.rolled(progressAt(3 * quarter));
        assertTrue(early > late, "the fog does not ease out");
    }

    @Test
    void mistHoldsThroughTheRollThenFades() {
        assertEquals(1f, FrostExplosionVisual.mist(0f), 0f);
        assertEquals(1f, FrostExplosionVisual.mist(progressAt(FrostExplosionVisual.ROLL_TICKS)), TOLERANCE);
        float midMist = FrostExplosionVisual.ROLL_TICKS + FrostExplosionVisual.MIST_TICKS / 2f;
        assertEquals(0.5f, FrostExplosionVisual.mist(progressAt(midMist)), TOLERANCE);
        assertEquals(0f, FrostExplosionVisual.mist(1f), TOLERANCE);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void sphereZoneIsTheFreezeRadiusCenteredOneBlockIntoTheWall(Direction face) {
        for (int stacks = 1; stacks <= MAX_FROST_STACKS; stacks++) {
            FrostExplosionVisual.FrostZone zone = FrostExplosionVisual.zone(AreaShape.SPHERE, stacks, face);
            float radius = AbilityMath.computeFreezeRadius(stacks);
            assertFalse(zone.boxy());
            assertEquals(radius, zone.halfX(), 0f);
            assertEquals(radius, zone.halfY(), 0f);
            assertEquals(radius, zone.halfZ(), 0f);
            assertEquals(0.5f - face.getStepX(), zone.centerX(), 0f);
            assertEquals(0.5f - face.getStepY(), zone.centerY(), 0f);
            assertEquals(0.5f - face.getStepZ(), zone.centerZ(), 0f);
        }
    }

    @ParameterizedTest
    @EnumSource(value = AreaShape.class, names = {"TUNNEL", "FLAT_CIRCLE"})
    void boxZoneIsTheFootprintsBounds(AreaShape shape) {
        int stacks = 3;
        AABB box = ChainFootprint.computeBounds(stacks, shape == AreaShape.FLAT_CIRCLE, Direction.UP);
        FrostExplosionVisual.FrostZone zone = FrostExplosionVisual.zone(shape, stacks, Direction.UP);
        assertTrue(zone.boxy());
        assertEquals(box.getCenter().x, zone.centerX(), TOLERANCE);
        assertEquals(box.getCenter().y, zone.centerY(), TOLERANCE);
        assertEquals(box.getCenter().z, zone.centerZ(), TOLERANCE);
        assertEquals(box.getXsize() / 2, zone.halfX(), TOLERANCE);
        assertEquals(box.getYsize() / 2, zone.halfY(), TOLERANCE);
        assertEquals(box.getZsize() / 2, zone.halfZ(), TOLERANCE);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.FROST_EXPLOSION);
    }
}
