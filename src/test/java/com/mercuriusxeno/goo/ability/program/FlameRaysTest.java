package com.mercuriusxeno.goo.ability.program;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Flames burns by a spray of rays inside its cone: a mob on a ray's line
 * within the range burns, a mob beside the cone does not, two mobs on one
 * line both burn, and a mob past the block a ray meets does not. Angles are
 * fixed through a stubbed random source and the block clip is stubbed
 * (decision flames-burns-by-a-spray-of-rays).
 */
class FlameRaysTest {

    private static final Vec3 APEX = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final double CONE_DEGREES = 20;
    private static final double RANGE = 6;
    /** A zombie-sized box: 0.6 wide, 1.95 tall. */
    private static final double HALF_WIDTH = 0.3;
    private static final double HEIGHT = 1.95;
    private static final FlameRays.BlockClip OPEN_AIR = (from, to) -> to;
    private static final Function<AABB, AABB> BOX_OF = box -> box;

    @Test
    void aMobOnARaysLineWithinTheRangeBurns() {
        AABB onTheLine = mobCenteredAt(APEX.add(EAST.scale(4)));
        List<FlameRays.Ray> rays = FlameRays.spray(APEX, EAST, CONE_DEGREES, RANGE, alongTheAxis(), OPEN_AIR);
        assertEquals(List.of(onTheLine), FlameRays.crossed(rays, List.of(onTheLine), BOX_OF));
    }

    @Test
    void aMobBesideTheConeDoesNotBurn() {
        // 3 blocks out and 1.5 to the side: its nearest edge sits about 22 degrees off the axis, the cone's rim 10.
        AABB beside = mobCenteredAt(APEX.add(EAST.scale(3)).add(0, 0, 1.5));
        List<FlameRays.Ray> rays = FlameRays.spray(APEX, EAST, CONE_DEGREES, RANGE, atTheRim(0), OPEN_AIR);
        assertTrue(FlameRays.crossed(rays, List.of(beside), BOX_OF).isEmpty());
    }

    @Test
    void twoMobsOnOneLineBothBurn() {
        AABB near = mobCenteredAt(APEX.add(EAST.scale(2)));
        AABB far = mobCenteredAt(APEX.add(EAST.scale(4.5)));
        List<FlameRays.Ray> rays = FlameRays.spray(APEX, EAST, CONE_DEGREES, RANGE, alongTheAxis(), OPEN_AIR);
        assertEquals(List.of(near, far), FlameRays.crossed(rays, List.of(near, far), BOX_OF));
    }

    @Test
    void aMobPastTheBlockARayMeetsDoesNotBurn() {
        AABB before = mobCenteredAt(APEX.add(EAST.scale(1.5)));
        AABB past = mobCenteredAt(APEX.add(EAST.scale(4.5)));
        FlameRays.BlockClip wallAtThree = (from, to) -> from.add(to.subtract(from).normalize().scale(3));
        List<FlameRays.Ray> rays = FlameRays.spray(APEX, EAST, CONE_DEGREES, RANGE, alongTheAxis(), wallAtThree);
        assertEquals(List.of(before), FlameRays.crossed(rays, List.of(before, past), BOX_OF));
    }

    @Test
    void aMobPastTheRangeDoesNotBurn() {
        AABB beyond = mobCenteredAt(APEX.add(EAST.scale(RANGE + 1)));
        List<FlameRays.Ray> rays = FlameRays.spray(APEX, EAST, CONE_DEGREES, RANGE, alongTheAxis(), OPEN_AIR);
        assertTrue(FlameRays.crossed(rays, List.of(beyond), BOX_OF).isEmpty());
    }

    private static AABB mobCenteredAt(Vec3 middle) {
        return new AABB(middle.x - HALF_WIDTH, middle.y - HEIGHT / 2, middle.z - HALF_WIDTH,
                middle.x + HALF_WIDTH, middle.y + HEIGHT / 2, middle.z + HALF_WIDTH);
    }

    /** Every ray straight down the axis: no tilt off it. */
    private static RandomSource alongTheAxis() {
        RandomSource random = mock(RandomSource.class);
        when(random.nextDouble()).thenReturn(0.0);
        return random;
    }

    /** Every ray tilted out to the cone's rim, turned the given share of a circle about the axis. */
    private static RandomSource atTheRim(double turn) {
        RandomSource random = mock(RandomSource.class);
        when(random.nextDouble()).thenReturn(1.0, turn, 1.0, turn, 1.0, turn, 1.0, turn, 1.0, turn, 1.0, turn,
                1.0, turn, 1.0, turn, 1.0, turn, 1.0, turn, 1.0, turn, 1.0, turn);
        return random;
    }
}
