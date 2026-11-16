package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.Mth;

/**
 * The gate trip's curves against the fixation's progress, zero as the gate takes hold and one as it carries the
 * caster: the sky dims to the star void first, the land drops into darkness behind it, and the moon swells from
 * vanilla's size, slowly and then rushing in, until it covers the whole view.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 */
final class GateTrip {

    /** How far off vanilla's sky draws the moon, and the moon quad's half width there. */
    static final float SKY_DISTANCE = 100F;
    private static final float VANILLA_MOON_HALF_WIDTH = 20F;
    /** The share of the moon sprite's width its disc spans: eight of its thirty-two texels. */
    static final double MOON_DISC_SHARE = 8.0 / 32.0;
    /** Vanilla's moon disc's half width, as the tangent of its angle from the view's center. */
    static final double RESTING_MOON_HALF_TANGENT = VANILLA_MOON_HALF_WIDTH / SKY_DISTANCE * MOON_DISC_SHARE;
    /** The widest view a player holds: vanilla's widest field of view slider on an ultrawide screen. */
    private static final double WIDEST_VERTICAL_FOV_DEGREES = 110.0;
    private static final double WIDEST_ASPECT = 21.0 / 9.0;
    /**
     * The widest view's half diagonal, as a tangent: a square the moon's disc covers it whatever way the disc
     * turns once its half width reaches this.
     */
    static final double WIDEST_VIEW_HALF_TANGENT = Math.tan(Math.toRadians(WIDEST_VERTICAL_FOV_DEGREES / 2))
            * Math.hypot(1.0, WIDEST_ASPECT);
    /** How far past the widest view the swollen moon reaches, so its edge never shows at the end. */
    private static final double SWOLLEN_MARGIN = 1.25;
    private static final double SWOLLEN_MOON_HALF_TANGENT = WIDEST_VIEW_HALF_TANGENT * SWOLLEN_MARGIN;

    /** The share of the fixation over which the sky dims to the star void. */
    private static final float SKY_DIM_START = 0F;
    private static final float SKY_DIM_END = 0.4F;
    /** The share of the fixation over which the land drops into darkness, after the sky begins. */
    private static final float LAND_DARK_START = 0.2F;
    private static final float LAND_DARK_END = 0.6F;
    /** The share of the fixation from which the moon swells, once the sky is dimming. */
    private static final float SWELL_START = 0.25F;

    private GateTrip() {
    }

    /**
     * How far the sky has dimmed to the star void.
     *
     * @param progress the share of the fixation run, zero to one
     * @return none at zero to full at one
     */
    static float skyDim(float progress) {
        return eased(progress, SKY_DIM_START, SKY_DIM_END);
    }

    /**
     * How far the land around the caster has dropped into darkness.
     *
     * @param progress the share of the fixation run, zero to one
     * @return none at zero to full at one
     */
    static float landDark(float progress) {
        return eased(progress, LAND_DARK_START, LAND_DARK_END);
    }

    /**
     * The moon disc's half width as the tangent of its angle from the view's center: vanilla's size until the
     * swell begins, then growing by an ever larger factor, so it creeps and then rushes in to cover the view.
     *
     * @param progress the share of the fixation run, zero to one
     * @return the half width's tangent
     */
    static double moonHalfTangent(float progress) {
        float swell = Mth.clamp((progress - SWELL_START) / (1F - SWELL_START), 0F, 1F);
        return RESTING_MOON_HALF_TANGENT
                * Math.pow(SWOLLEN_MOON_HALF_TANGENT / RESTING_MOON_HALF_TANGENT, swell * swell);
    }

    /**
     * A smooth rise from none to full over a window of the fixation.
     *
     * @param progress the share of the fixation run
     * @param start    where the rise begins
     * @param end      where it reaches full
     * @return none before the window, full after it
     */
    private static float eased(float progress, float start, float end) {
        return (float) Mth.smoothstep(Mth.clamp((progress - start) / (end - start), 0F, 1F));
    }
}
