package com.mercuriusxeno.goo.item;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A fleck leaving a block face: its speed along the face's normal reversed and
 * scaled by its type's elasticity, its speed along the face kept
 * (decision flecks-move-by-flexible-goo-physics-and-stay-items).
 */
class FleckReboundTest {

    private static final double WITHIN = 1e-9;
    private static final Vec3 FLOOR_UP = new Vec3(0, 1, 0);
    private static final Vec3 WALL_EAST = new Vec3(1, 0, 0);

    @ParameterizedTest
    @ValueSource(floats = {0f, 0.5f, 1f})
    void aFleckStrikingAFloorLeavesItAtItsElasticitysShareOfItsFall(float elasticity) {
        Vec3 striking = new Vec3(0.1, -0.5, 0.2);

        Vec3 leaving = FleckRebound.rebound(striking, FLOOR_UP, elasticity);

        assertEquals(-striking.y * elasticity, leaving.y, WITHIN);
        assertEquals(striking.x, leaving.x, WITHIN);
        assertEquals(striking.z, leaving.z, WITHIN);
    }

    @ParameterizedTest
    @ValueSource(floats = {0f, 0.5f, 1f})
    void aFleckStrikingAWallLeavesItAtItsElasticitysShareOfItsApproach(float elasticity) {
        Vec3 striking = new Vec3(-0.4, -0.1, 0.3);

        Vec3 leaving = FleckRebound.rebound(striking, WALL_EAST, elasticity);

        assertEquals(-striking.x * elasticity, leaving.x, WITHIN);
        assertEquals(striking.y, leaving.y, WITHIN);
        assertEquals(striking.z, leaving.z, WITHIN);
    }
}
