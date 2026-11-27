package com.mercuriusxeno.goo.lunar;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.Noise;
import java.util.ArrayList;
import java.util.List;

/**
 * The low-frequency masks lunar's features gather by: read from the seeded crater noise far slower than its rolls, a
 * crater field mask crowds craters into fields with long bare plains between, and a boulder field mask scatters
 * boulders through fields of their own. The crater field thins where the maria noise reads maria, so a maria reads
 * mostly bare.
 * decision lunar-features-cluster-with-bare-ground-between
 */
public final class FieldMasks {

    /** A mask swings across this many blocks, so a field and the plain beside it each run hundreds of blocks. */
    static final double MASK_WAVELENGTH = 800.0;
    /** A crater field starts where its mask reads over this, and holds its full chance this far past it. */
    static final double CRATER_FIELD_FLOOR = 0.0;
    static final double CRATER_FIELD_RAMP = 0.3;
    /** Where the maria noise reads maria, a crater field keeps this share of its chance. */
    static final double MARIA_CRATER_SHARE = 0.25;
    /** A boulder field starts where its mask reads over this, and is at its thickest this far past it. */
    static final double BOULDER_FIELD_FLOOR = 0.15;
    static final double BOULDER_FIELD_RAMP = 0.3;
    /** A chunk at a boulder field's thickest scatters this many boulders. */
    static final int BOULDERS_PER_CHUNK = 3;
    private static final int CHUNK_SIDE = 16;
    /** A chunk's boulder field is read at the chunk's middle. */
    private static final double CHUNK_MIDDLE = CHUNK_SIDE / 2.0;
    /** The masks read the crater noise off its lattice, each at heights of its own, apart from every roll. */
    private static final double CRATER_MASK_Y = 517.37;
    private static final double BOULDER_MASK_Y = 733.91;
    private static final double MASK_OFFSET = 0.413;
    /** A chunk's scattered boulders roll on indices of their own, two to a boulder, past the count's roll. */
    private static final int BOULDER_COUNT_ROLL = 300;
    private static final int BOULDER_ROLLS = 301;
    private static final int ROLLS_PER_BOULDER = 2;

    private FieldMasks() {
    }

    private static double maskAt(Noise noise, double maskY, double x, double z) {
        return noise.get(x / MASK_WAVELENGTH + MASK_OFFSET, maskY, z / MASK_WAVELENGTH + MASK_OFFSET);
    }

    private static double share(double mask, double floor, double ramp) {
        return Mth.clamp((mask - floor) / ramp, 0.0, 1.0);
    }

    /**
     * The share of its full chance a crater keeps at a spot: none on the plains between crater fields, all of it deep
     * in a field, and a quarter of that in maria.
     *
     * @param craterNoise the seeded crater noise
     * @param mariaValue  the maria noise at the spot, above zero in maria
     * @param x           the spot's x
     * @param z           the spot's z
     * @return the share, in [0, 1]
     */
    static double craterFieldShare(Noise craterNoise, double mariaValue, double x, double z) {
        double field = share(maskAt(craterNoise, CRATER_MASK_Y, x, z), CRATER_FIELD_FLOOR, CRATER_FIELD_RAMP);
        return mariaValue > 0.0 ? field * MARIA_CRATER_SHARE : field;
    }

    /**
     * The spots a chunk's share of a boulder field scatters boulders onto: none outside a field, up to
     * {@link #BOULDERS_PER_CHUNK} deep in one, each rolled from the chunk's own rolls.
     *
     * @param craterNoise the seeded crater noise
     * @param chunkX      the chunk's x
     * @param chunkZ      the chunk's z
     * @return the spots, at y zero
     */
    public static List<BlockPos> boulderFieldSpots(Noise craterNoise, int chunkX, int chunkZ) {
        int minX = chunkX * CHUNK_SIDE;
        int minZ = chunkZ * CHUNK_SIDE;
        double thickness = share(maskAt(craterNoise, BOULDER_MASK_Y, minX + CHUNK_MIDDLE, minZ + CHUNK_MIDDLE),
                BOULDER_FIELD_FLOOR, BOULDER_FIELD_RAMP);
        int count = (int) Math.floor(thickness * BOULDERS_PER_CHUNK
                + CraterField.rollFrom(craterNoise, chunkX, chunkZ, BOULDER_COUNT_ROLL));
        List<BlockPos> spots = new ArrayList<>(count);
        for (int boulder = 0; boulder < Math.min(count, BOULDERS_PER_CHUNK) && thickness > 0.0; boulder++) {
            int roll = BOULDER_ROLLS + boulder * ROLLS_PER_BOULDER;
            spots.add(new BlockPos(minX + (int) (CraterField.rollFrom(craterNoise, chunkX, chunkZ, roll) * CHUNK_SIDE),
                    0, minZ + (int) (CraterField.rollFrom(craterNoise, chunkX, chunkZ, roll + 1) * CHUNK_SIDE)));
        }
        return spots;
    }
}
