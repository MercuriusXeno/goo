package com.mercuriusxeno.goo.lunar;

/**
 * Biome noises reading one value everywhere, standing in for the seeded maria and basins noises a giant crater's
 * centre reads.
 *
 * @param maria  the maria reading, above zero in maria
 * @param basins the basins reading, from GiantCraterField.BASINS_FLOOR up in the shadowed basins
 */
record FixedGround(double maria, double basins) implements CentreGround {

    @Override
    public double mariaAt(double x, double z) {
        return maria;
    }

    @Override
    public double basinsAt(double x, double z) {
        return basins;
    }
}
