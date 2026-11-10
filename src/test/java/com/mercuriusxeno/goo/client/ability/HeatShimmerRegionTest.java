package com.mercuriusxeno.goo.client.ability;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The heat shimmer is a box on the screen centered where the flame's reach
 * point projects, no wider or taller than its named share of the screen
 * (decision heat-wave-shrinks-to-a-local-shimmer).
 */
class HeatShimmerRegionTest {

    private static final float EPSILON = 1e-5f;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    private final Matrix4f view = new Matrix4f().rotateY((float) Math.toRadians(20));
    private final Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70),
            (float) WIDTH / HEIGHT, 0.05f, 1000f);

    @Test
    void theBoxIsCenteredWhereTheReachPointProjects() {
        Vector3f reach = new Vector3f(1.5f, -0.75f, -4f);
        Vector4f clip = new Matrix4f(projection).mul(view).transform(new Vector4f(reach, 1f));
        float projectedU = clip.x / clip.w * 0.5f + 0.5f;
        float projectedV = clip.y / clip.w * 0.5f + 0.5f;

        HeatWaveEffect.ShimmerRegion region = HeatWaveEffect.shimmerAround(reach, view, projection, WIDTH, HEIGHT);

        assertEquals(projectedU, region.centerU(), EPSILON);
        assertEquals(projectedV, region.centerV(), EPSILON);
    }

    @Test
    void theBoxSpansNoMoreThanItsShareOfTheScreen() {
        HeatWaveEffect.ShimmerRegion region = HeatWaveEffect.shimmerAround(new Vector3f(0, 0, -3f), view,
                projection, WIDTH, HEIGHT);

        assertTrue(region.widthShare() <= HeatWaveEffect.SHIMMER_SHARE + EPSILON);
        assertTrue(region.heightShare() <= HeatWaveEffect.SHIMMER_SHARE + EPSILON);
    }

    @Test
    void aTallScreenKeepsTheBoxWithinItsShareToo() {
        HeatWaveEffect.ShimmerRegion region = HeatWaveEffect.shimmerAround(new Vector3f(0, 0, -3f), view,
                projection, HEIGHT, WIDTH);

        assertTrue(region.widthShare() <= HeatWaveEffect.SHIMMER_SHARE + EPSILON);
        assertTrue(region.heightShare() <= HeatWaveEffect.SHIMMER_SHARE + EPSILON);
    }

    @Test
    void aReachPointBehindTheCameraShowsNoShimmer() {
        assertNull(HeatWaveEffect.shimmerAround(new Vector3f(0, 0, 3f), new Matrix4f(), projection, WIDTH, HEIGHT));
    }
}
