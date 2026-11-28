package com.mercuriusxeno.goo.lunar;

import net.minecraft.world.level.levelgen.synth.Noise;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lunar's boulders gather into fields: counted region by region over a wide area, the boulders the boulder fields
 * scatter leave some regions bare and crowd others with several. The noise is built from the shipped noise's settings
 * and a fixed seed, with no registry.
 * decision lunar-features-cluster-with-bare-ground-between
 */
class FieldMasksTest {

    /** A region is this many chunks on a side, 320 blocks. */
    private static final int REGION_CHUNKS = 20;
    /** The sampled area is this many regions on a side, 12800 blocks. */
    private static final int REGIONS = 40;
    /** A crowded region holds at least this many boulders. */
    private static final int SEVERAL_BOULDERS = 4;

    private static int bouldersIn(Noise noise, int regionX, int regionZ) {
        int count = 0;
        for (int chunkX = regionX * REGION_CHUNKS; chunkX < (regionX + 1) * REGION_CHUNKS; chunkX++) {
            for (int chunkZ = regionZ * REGION_CHUNKS; chunkZ < (regionZ + 1) * REGION_CHUNKS; chunkZ++) {
                count += FieldMasks.boulderFieldSpots(noise, chunkX, chunkZ).size();
            }
        }
        return count;
    }

    @Test
    void someRegionsHoldNoBoulderAndOthersHoldSeveral() {
        Noise noise = LunarTestNoises.crater();
        int bare = 0;
        int crowded = 0;
        for (int regionX = 0; regionX < REGIONS; regionX++) {
            for (int regionZ = 0; regionZ < REGIONS; regionZ++) {
                int boulderCount = bouldersIn(noise, regionX, regionZ);
                bare += boulderCount == 0 ? 1 : 0;
                crowded += boulderCount >= SEVERAL_BOULDERS ? 1 : 0;
            }
        }
        assertTrue(bare > 0, "no region of " + REGIONS * REGIONS + " is bare of boulders");
        assertTrue(crowded > 0, "no region holds " + SEVERAL_BOULDERS + " boulders");
    }
}
