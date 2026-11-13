package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Petrify's dust pours from the glove and trails behind a turning aim rather
 * than turning with the hand, and, the aim held still, fills the cone the
 * delivery strikes out to its reach; its pipelines, the fog, the stone patches
 * and the block mingle, name shaders the classpath holds
 * (decisions petrify-fog-is-billowing-roiling-dust, petrify-stone-encasement-and-calcify-map).
 */
class PetrifyFogTest {

    /** rock_petrify.json's delivery: range 6, cone 30 degrees. */
    private static final double RANGE = 6;
    private static final double HALF_ANGLE = Math.toRadians(30 / 2.0);
    private static final Vec3 GLOVE = new Vec3(10.4, 64.2, -3.6);
    private static final Vec3 AIM = new Vec3(0, 0, 1);
    private static final Vec3 TURNED_AIM = new Vec3(1, 0, 0);
    private static final double TICK = 1;
    private static final double AT_THE_GLOVE = 1e-9;
    /** Far enough off the new aim's axis that the puff plainly did not turn with the hand. */
    private static final double OFF_THE_AXIS = 0.5;
    private static final int GRID = 12;
    private static final int AGE_STEPS = 96;
    private static final double REACH_SLACK = 1e-6;
    private static final double ANGLE_SLACK = 1e-6;
    /** The settled fog's widest dust stands within this share of the half angle from it. */
    private static final double WIDEST_SHARE = 0.9;

    private static double offAxis(Vec3 point, Vec3 origin, Vec3 axis) {
        Vec3 from = point.subtract(origin);
        return from.subtract(axis.scale(from.dot(axis))).length();
    }

    private static List<PetrifyPuffs.Puff> settledFog() {
        List<PetrifyPuffs.Puff> puffs = new ArrayList<>();
        for (int lean = 0; lean <= GRID; lean++) {
            for (int around = 0; around < GRID; around++) {
                puffs.add(PetrifyPuffs.pour(GLOVE, AIM, HALF_ANGLE, 0, (double) lean / GRID,
                        (double) around / GRID, (float) ((lean * GRID + around) % GRID) / GRID));
            }
        }
        return puffs;
    }

    @Nested
    class Trailing {

        @Test
        void dustPouredBeforeTheTurnTrailsOffTheNewAimsAxis() {
            PetrifyPuffs.Puff before = PetrifyPuffs.pour(GLOVE, AIM, HALF_ANGLE, 0, 0, 0, 0.3f);
            double later = TICK + TICK / 2;
            Vec3 where = PetrifyPuffs.positionAt(before, later, RANGE, HALF_ANGLE);
            assertTrue(offAxis(where, GLOVE, TURNED_AIM) > OFF_THE_AXIS,
                    "the old puff stands " + offAxis(where, GLOVE, TURNED_AIM) + " off the turned aim");
        }

        @Test
        void dustJustPouredStandsAtTheGlove() {
            PetrifyPuffs.Puff poured = PetrifyPuffs.pour(GLOVE, TURNED_AIM, HALF_ANGLE, TICK, 1, 0.4, 0.7f);
            assertEquals(0, PetrifyPuffs.positionAt(poured, TICK, RANGE, HALF_ANGLE).distanceTo(GLOVE),
                    AT_THE_GLOVE);
        }
    }

    @Nested
    class Cone {

        @Test
        void theSettledFogsFarthestDustStandsAtTheReachAlongTheAim() {
            double farthest = 0;
            for (PetrifyPuffs.Puff puff : settledFog()) {
                for (int step = 0; step <= AGE_STEPS; step++) {
                    double now = PetrifyPuffs.LIFE_TICKS * step / AGE_STEPS;
                    Vec3 where = PetrifyPuffs.positionAt(puff, now, RANGE, HALF_ANGLE);
                    farthest = Math.max(farthest, where.subtract(GLOVE).dot(AIM));
                }
            }
            assertTrue(farthest <= RANGE + REACH_SLACK, "dust stands " + farthest + " out");
            assertTrue(farthest > RANGE * Math.cos(HALF_ANGLE), "dust reaches only " + farthest);
        }

        @Test
        void theSettledFogsWidestDustStandsAtTheConesHalfAngle() {
            double widest = 0;
            for (PetrifyPuffs.Puff puff : settledFog()) {
                for (int step = 1; step <= AGE_STEPS; step++) {
                    double now = PetrifyPuffs.LIFE_TICKS * step / AGE_STEPS;
                    Vec3 from = PetrifyPuffs.positionAt(puff, now, RANGE, HALF_ANGLE).subtract(GLOVE).normalize();
                    widest = Math.max(widest, Math.acos(Math.min(1, from.dot(AIM))));
                }
            }
            assertTrue(widest <= HALF_ANGLE + ANGLE_SLACK, "dust spreads to " + Math.toDegrees(widest));
            assertTrue(widest >= HALF_ANGLE * WIDEST_SHARE, "dust spreads only to " + Math.toDegrees(widest));
        }
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.PETRIFY_FOG);
        PipelineShaders.assertExist(GooRenderTypes.PETRIFY_STONE);
        PipelineShaders.assertExist(GooRenderTypes.BLOCK_MINGLE);
        PipelineShaders.assertExist(GooRenderTypes.BORE_DRILL);
    }
}
