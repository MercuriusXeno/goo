package com.mercuriusxeno.goo.lunar;

/**
 * The biome noises a giant crater's centre reads, so the crater takes its biome's character.
 * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
 */
interface CentreGround {

    /**
     * @param x the centre's x
     * @param z the centre's z
     * @return the maria noise there, above zero in maria
     */
    double mariaAt(double x, double z);

    /**
     * @param x the centre's x
     * @param z the centre's z
     * @return the basins noise there, from {@link GiantCraterField#BASINS_FLOOR} up in the shadowed basins
     */
    double basinsAt(double x, double z);
}
