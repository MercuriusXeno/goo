package com.mercuriusxeno.goo.ability.reap;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which bodies a Reap blade cuts: a body any point of the blade
 * reaches, and no body the blade passes clear of.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class BladeFlightsTest {

    private static final AABB COW = new AABB(2.55, 0, 2.05, 3.45, 1.4, 2.95);

    @Test
    void aBodyABladePointReachesIsCut() {
        assertTrue(BladeFlights.cuts(COW, List.of(new Vec3(0, 1, 0), new Vec3(3, 1, 2.5))));
    }

    @Test
    void aBodyAPointGrazesIsCut() {
        assertTrue(BladeFlights.cuts(COW, List.of(new Vec3(3.55, 1, 2.5))));
    }

    @Test
    void aBodyTheBladePassesClearOfIsNotCut() {
        assertFalse(BladeFlights.cuts(COW, List.of(new Vec3(3, 1, 4), new Vec3(3, 2, 2.5), new Vec3(0, 1, 0))));
    }
}
