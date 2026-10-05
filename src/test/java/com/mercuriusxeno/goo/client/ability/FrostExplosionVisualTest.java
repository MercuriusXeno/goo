package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FrostExplosionVisual's fog ring: its timing, the reach it spreads to, the
 * snowflakes' launch, and the shader pair its pipeline names.
 */
class FrostExplosionVisualTest {

    private static final float TOLERANCE = 1e-5f;
    private static final int MAX_FROST_STACKS = 4;
    private static final int ANGLES = 16;
    private static final float SPEED = 0.3f;

    private static float progressAt(float tick) {
        return tick / FrostExplosionVisual.DURATION_TICKS;
    }

    @Test
    void ringSpreadsOverTwentyTicksFastThenSlow() {
        assertEquals(0f, FrostExplosionVisual.spread(0f), 0f);
        assertEquals(1f, FrostExplosionVisual.spread(progressAt(FrostExplosionVisual.SPREAD_TICKS)), TOLERANCE);
        assertEquals(1f, FrostExplosionVisual.spread(1f), TOLERANCE);
        float quarter = FrostExplosionVisual.SPREAD_TICKS / 4f;
        float early = FrostExplosionVisual.spread(progressAt(quarter));
        float late = 1f - FrostExplosionVisual.spread(progressAt(3 * quarter));
        assertTrue(early > late, "the ring does not ease out");
    }

    @Test
    void fogHoldsWhileTheRingSpreadsThenFades() {
        assertEquals(1f, FrostExplosionVisual.fog(0f), 0f);
        assertEquals(1f, FrostExplosionVisual.fog(progressAt(FrostExplosionVisual.SPREAD_TICKS)), TOLERANCE);
        float midFade = FrostExplosionVisual.SPREAD_TICKS + FrostExplosionVisual.FADE_TICKS / 2f;
        assertEquals(0.5f, FrostExplosionVisual.fog(progressAt(midFade)), TOLERANCE);
        assertEquals(0f, FrostExplosionVisual.fog(1f), TOLERANCE);
    }

    @Test
    void ringReachesOneBlockPerStack() {
        for (int stacks = 1; stacks <= MAX_FROST_STACKS; stacks++) {
            assertEquals(stacks, FrostExplosionVisual.zoneReach(stacks), 0f);
        }
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void snowflakesLaunchOutwardAcrossTheRingsPlane(Direction face) {
        for (int i = 0; i < ANGLES; i++) {
            double angle = 2 * Math.PI * i / ANGLES;
            Vec3 velocity = FrostExplosionVisual.snowflakeVelocity(face, angle, SPEED);
            double alongFace = velocity.x * face.getStepX() + velocity.y * face.getStepY() + velocity.z * face.getStepZ();
            assertEquals(0.0, alongFace, TOLERANCE, "a snowflake leaves the plane on " + face);
            assertEquals(SPEED, velocity.length(), TOLERANCE);
        }
        Vec3 first = FrostExplosionVisual.snowflakeVelocity(face, 0, SPEED);
        Vec3 opposite = FrostExplosionVisual.snowflakeVelocity(face, Math.PI, SPEED);
        assertEquals(-SPEED * SPEED, first.dot(opposite), TOLERANCE, "snowflakes do not spread outward");
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.FROST_EXPLOSION);
    }
}
