package com.mercuriusxeno.goo.client.ber;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A melting block warps like wax, one smooth shape whose top sinks, top
 * edges droop and foot bulges, standing as it was while whole; and its goo
 * patches form in a scatter, all formed by the end
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class MeltMeshTest {

    private static final double DELTA = 1e-6;

    @Test
    void aWholeBlockStandsAsItWas() {
        Vec3 corner = MeltMesh.warp(1f, 1f, 0f, 0f, 37f);

        assertEquals(1.0, corner.x, DELTA);
        assertEquals(1.0, corner.y, DELTA);
        assertEquals(0.0, corner.z, DELTA);
    }

    @Test
    void aMeltedBlocksTopSinksAndItsEdgesDroopBelowItsMiddle() {
        Vec3 middleTop = MeltMesh.warp(0.5f, 1f, 0.5f, 1f, 0f);
        Vec3 edgeTop = MeltMesh.warp(1f, 1f, 0.5f, 1f, 0f);

        assertTrue(middleTop.y < 0.5);
        assertTrue(edgeTop.y < middleTop.y);
    }

    @Test
    void aMeltedBlocksFootBulgesPastItsFootprint() {
        Vec3 foot = MeltMesh.warp(1f, 0f, 0.5f, 1f, 0f);

        assertTrue(foot.x > 1.0 + MeltMesh.BULGE * 0.4);
        assertEquals(0.0, foot.y, DELTA);
    }

    @Test
    void theWarpIsSmoothAlongAFace() {
        Vec3 low = MeltMesh.warp(1f, 0.5f, 0.5f, 0.6f, 0f);
        Vec3 high = MeltMesh.warp(1f, 0.5f + 1f / MeltMesh.GRID, 0.5f, 0.6f, 0f);

        assertTrue(Math.abs(high.x - low.x) < 0.1);
    }

    @Test
    void patchesFormInAScatterAndAllByTheEnd() {
        assertEquals(0f, MeltMesh.patchFormed(0.1f, 0.9), DELTA);
        assertTrue(MeltMesh.patchFormed(0.3f, 0.1) > 0f);
        assertEquals(1f, MeltMesh.patchFormed(1f, 0.999), DELTA);
    }
}
