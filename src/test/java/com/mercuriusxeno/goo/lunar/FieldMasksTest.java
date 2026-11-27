package com.mercuriusxeno.goo.lunar;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lunar's features gather by region: counted region by region over a wide area, the craters the crater field holds
 * and the boulders the boulder fields scatter leave some regions holding neither and crowd others with several, and
 * maria holds fewer craters than ground outside it. The noises are built from the shipped noises' settings and a
 * fixed seed, with no registry.
 * decision lunar-features-cluster-with-bare-ground-between
 */
class FieldMasksTest {

    /** A region is this many crater cells on a side, 320 blocks. */
    private static final int REGION_CELLS = 8;
    /** The sampled area is this many regions on a side, 12800 blocks. */
    private static final int REGIONS = 40;
    /** A dense region holds at least this many craters and this many boulders. */
    private static final int SEVERAL_CRATERS = 8;
    private static final int SEVERAL_BOULDERS = 4;
    private static final int CHUNK_SIDE = 16;
    private static final long SEED = 20_261_126L;

    private static Noise craterNoise() {
        return NormalNoise.builder().setBaseOctave(0).setBaseAmplitude(0.955388882960065).build()
                .create(RandomSource.create(SEED));
    }

    private static Noise mariaNoise() {
        return NormalNoise.builder().setBaseOctave(-9).setOctaveCount(2).setAmplitudeModifier(1, 0.5)
                .setBaseAmplitude(1.0504451407960578).build().create(RandomSource.create(SEED + 1));
    }

    private static int cratersIn(CraterField craters, int regionX, int regionZ) {
        int count = 0;
        for (int cellX = regionX * REGION_CELLS; cellX < (regionX + 1) * REGION_CELLS; cellX++) {
            for (int cellZ = regionZ * REGION_CELLS; cellZ < (regionZ + 1) * REGION_CELLS; cellZ++) {
                count += craters.craterInCell(cellX, cellZ).isPresent() ? 1 : 0;
            }
        }
        return count;
    }

    private static int bouldersIn(Noise noise, int regionX, int regionZ) {
        int regionChunks = REGION_CELLS * CraterField.CELL / CHUNK_SIDE;
        int count = 0;
        for (int chunkX = regionX * regionChunks; chunkX < (regionX + 1) * regionChunks; chunkX++) {
            for (int chunkZ = regionZ * regionChunks; chunkZ < (regionZ + 1) * regionChunks; chunkZ++) {
                count += FieldMasks.boulderFieldSpots(noise, chunkX, chunkZ).size();
            }
        }
        return count;
    }

    @Test
    void someRegionsHoldNoFeatureAndOthersHoldSeveral() {
        Noise noise = craterNoise();
        CraterField craters = new CraterField(noise, mariaNoise());
        int bare = 0;
        int crowded = 0;
        int boulderFields = 0;
        for (int regionX = 0; regionX < REGIONS; regionX++) {
            for (int regionZ = 0; regionZ < REGIONS; regionZ++) {
                int craterCount = cratersIn(craters, regionX, regionZ);
                int boulderCount = bouldersIn(noise, regionX, regionZ);
                bare += craterCount == 0 && boulderCount == 0 ? 1 : 0;
                crowded += craterCount >= SEVERAL_CRATERS ? 1 : 0;
                boulderFields += boulderCount >= SEVERAL_BOULDERS ? 1 : 0;
            }
        }
        assertTrue(bare > 0, "no region of " + REGIONS * REGIONS + " holds neither a crater nor a boulder");
        assertTrue(crowded > 0, "no region holds " + SEVERAL_CRATERS + " craters");
        assertTrue(boulderFields > 0, "no region holds " + SEVERAL_BOULDERS + " boulders");
    }

    @Test
    void mariaHoldsFewerCratersThanTheGroundOutsideIt() {
        Noise noise = craterNoise();
        Noise maria = mariaNoise();
        CraterField craters = new CraterField(noise, maria);
        int[] cells = new int[2];
        int[] holding = new int[2];
        int reach = REGIONS * REGION_CELLS;
        for (int cellX = 0; cellX < reach; cellX++) {
            for (int cellZ = 0; cellZ < reach; cellZ++) {
                double x = (cellX + 0.5) * CraterField.CELL;
                double z = (cellZ + 0.5) * CraterField.CELL;
                int side = maria.get(x, 0.0, z) > 0.0 ? 0 : 1;
                cells[side]++;
                holding[side] += craters.craterInCell(cellX, cellZ).isPresent() ? 1 : 0;
            }
        }
        double mariaShare = (double) holding[0] / cells[0];
        double outsideShare = (double) holding[1] / cells[1];
        assertTrue(mariaShare < outsideShare, "maria holds craters in " + mariaShare + " of its cells, the ground "
                + "outside it in " + outsideShare);
    }
}
