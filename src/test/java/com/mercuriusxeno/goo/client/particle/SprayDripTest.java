package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A drawn spray drip flies the arc the server's drip flies from the same
 * launch, standing where it stands each tick and splatting on the tick it
 * strikes; only its color is rolled, between its palette's ends.
 * goop-and-slick-spray-throws-goo-drips-that-splat
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 */
class SprayDripTest {

    private static final Vec3 APEX = new Vec3(0.3, 1.3, 0.2);
    /** A bound on the steps a drip may fly, so a drip that never lands fails rather than hangs. */
    private static final int MAX_STEPS = 200;
    /** The floor's top face at y 0, the only block a segment meets. */
    private static final CoatRay.BlockClip FLOOR = (from, to) -> from.y >= 0 && to.y < 0
            ? Optional.of(new CoatRay.BlockStrike(from.add(to.subtract(from).scale(from.y / (from.y - to.y))),
                    BlockPos.containing(to.x, -1, to.z), Direction.UP))
            : Optional.empty();
    private static final DripArc.Airspace INSIDE_THE_WORLD = point -> point.y >= -64;
    private static final Vec3 GOOP_LAUNCH = new Vec3(0.6, 0.05, 0.1);

    @Test
    void aDrawnDripStandsWhereTheServersDripStandsEachTickAndSplatsOnItsStrike() {
        SprayDrip drip = SprayDrip.launch(GOOP_LAUNCH, BlendPalette.GOOP_SLIME_GREENS, RandomSource.create(1L));
        Vec3 drawn = APEX;
        Vec3 drawnVelocity = drip.velocity();
        Vec3 served = APEX;
        Vec3 servedVelocity = GOOP_LAUNCH;
        for (int flown = 0; flown < MAX_STEPS; flown++) {
            SprayDrip.Step step = drip.step(drawn, drawnVelocity, FLOOR, INSIDE_THE_WORLD);
            DripArc.Step arc = DripArc.step(served, servedVelocity, FLOOR, INSIDE_THE_WORLD);
            assertEquals(arc.position(), step.position(), "tick " + flown + " should leave both drips on one point");
            assertEquals(arc.strike().isPresent(), step.splats(), "the drawn drip should splat on the tick the"
                    + " server's drip strikes, tick " + flown);
            if (arc.ended()) {
                return;
            }
            drawn = step.position();
            drawnVelocity = step.velocity();
            served = arc.position();
            servedVelocity = arc.velocity();
        }
        throw new AssertionError("the drips should strike the floor within " + MAX_STEPS + " steps");
    }

    @Test
    void aDripsColorLiesBetweenItsPalettesEnds() {
        BlendPalette blues = BlendPalette.SLICK_SLIME_BLUES;
        for (long seed = 0; seed < 16; seed++) {
            int rgb = SprayDrip.launch(GOOP_LAUNCH, blues, RandomSource.create(seed)).rgb();
            assertBetween(blues.darkRed(), blues.lightRed(), (rgb >> 16 & 0xFF) / 255f, "red");
            assertBetween(blues.darkGreen(), blues.lightGreen(), (rgb >> 8 & 0xFF) / 255f, "green");
            assertBetween(blues.darkBlue(), blues.lightBlue(), (rgb & 0xFF) / 255f, "blue");
        }
    }

    private static void assertBetween(float one, float other, float channel, String name) {
        float slack = 1f / 255f;
        assertTrue(channel >= Math.min(one, other) - slack && channel <= Math.max(one, other) + slack,
                name + " " + channel + " should lie between " + one + " and " + other);
    }
}
