package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.GooClientConfig;
import com.mercuriusxeno.goo.GooClientConfig.NetherHoleShape;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests that the nether hole style follows the configured shape, and that
 * the client config defaults to the sphere with the lens off, the cube
 * being the shape a player opts into.
 */
class NetherHoleStylesTest {

    @Test
    void theSphereShapeAnswersTheSphereStyle() {
        assertSame(NetherHoleStyles.SPHERE, NetherHoleStyles.forShape(NetherHoleShape.SPHERE));
    }

    @Test
    void theCubeShapeAnswersTheCubeStyle() {
        assertSame(NetherHoleStyles.CUBE, NetherHoleStyles.forShape(NetherHoleShape.CUBE));
    }

    @Test
    void theConfigDefaultsToTheSphere() {
        assertEquals(NetherHoleShape.SPHERE, GooClientConfig.NETHER_HOLE_SHAPE.getDefault());
    }

    @Test
    void theDefaultShapeAnswersTheSphereStyle() {
        assertSame(NetherHoleStyles.SPHERE,
            NetherHoleStyles.forShape(GooClientConfig.NETHER_HOLE_SHAPE.getDefault()));
    }

    @Test
    void theConfigDefaultsToTheLensOff() {
        assertFalse(GooClientConfig.SHOW_NETHER_LENS.getDefault());
    }
}
