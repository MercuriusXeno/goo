package com.mercuriusxeno.goo.client;

/**
 * The ripple goo_fluid_surface.vsh lifts a fluid surface by, ported to Java so a tile
 * floating on the surface rises and falls with the wave at its own spot (decision
 * each-tile-bobs-with-the-ripple). The constants must match the shader's.
 */
public final class SurfaceRipple {

    /** Primary wave cycles per 24000-tick day; must match PRIMARY_CYCLES_PER_DAY in the shader. */
    public static final float PRIMARY_CYCLES_PER_DAY = 600f;
    /** Secondary wave cycles per day; must match SECONDARY_CYCLES_PER_DAY in the shader. */
    public static final float SECONDARY_CYCLES_PER_DAY = 420f;
    /** Primary spatial frequency in radians per block; must match PRIMARY_WAVENUMBER in the shader. */
    public static final float PRIMARY_WAVENUMBER = 9f;
    /** Secondary spatial frequency in radians per block; must match SECONDARY_WAVENUMBER in the shader. */
    public static final float SECONDARY_WAVENUMBER = 13f;
    /** The primary wave's direction across XZ. */
    public static final float PRIMARY_DIRECTION_X = 0.8f;
    /** The primary wave's direction across XZ. */
    public static final float PRIMARY_DIRECTION_Z = 0.6f;

    /** The ticks in a day, over which GameTime runs from zero to one. */
    private static final long TICKS_PER_DAY = 24_000L;
    private static final double TAU = 2.0 * Math.PI;
    private static final double SECONDARY_DIRECTION_X = -0.5;
    private static final double SECONDARY_DIRECTION_Z = 0.87;
    private static final float HALF = 0.5f;

    private SurfaceRipple() {
    }

    /**
     * Returns the shader's GameTime uniform: the fraction of the day, as vanilla's
     * GlobalSettingsUniform computes it.
     *
     * @param gameTime    the level's game time in ticks
     * @param partialTick the fraction of the tick elapsed
     * @return the fraction of the 24000-tick day, zero to one
     */
    public static float dayFraction(long gameTime, float partialTick) {
        return (gameTime % TICKS_PER_DAY + partialTick) / TICKS_PER_DAY;
    }

    /**
     * Returns the ripple's height at a world spot, from minus one to one, before the
     * amplitude scales it.
     *
     * @param worldX   the world X
     * @param worldZ   the world Z
     * @param gameTime the fraction of the day, as {@link #dayFraction} answers
     * @return the unscaled wave height
     */
    public static float at(double worldX, double worldZ, float gameTime) {
        double primary = Math.sin((worldX * PRIMARY_DIRECTION_X + worldZ * PRIMARY_DIRECTION_Z) * PRIMARY_WAVENUMBER
                + gameTime * TAU * PRIMARY_CYCLES_PER_DAY);
        double secondary = Math.sin((worldX * SECONDARY_DIRECTION_X + worldZ * SECONDARY_DIRECTION_Z)
                * SECONDARY_WAVENUMBER - gameTime * TAU * SECONDARY_CYCLES_PER_DAY);
        return (float) (HALF * (primary + secondary));
    }

    /**
     * The ripple over one block at one moment: the block's world origin and the day
     * fraction, so a block-relative spot answers the wave the surface draws there.
     *
     * @param originX  the block's world X
     * @param originZ  the block's world Z
     * @param gameTime the fraction of the day
     */
    public record Field(double originX, double originZ, float gameTime) {

        /**
         * @param localX the block-relative X
         * @param localZ the block-relative Z
         * @return the unscaled wave height there
         */
        public float at(float localX, float localZ) {
            return SurfaceRipple.at(originX + localX, originZ + localZ, gameTime);
        }
    }
}
