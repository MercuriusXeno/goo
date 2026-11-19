package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.astral.LunarFixation;
import com.mercuriusxeno.goo.client.zone.LunarSky;
import net.minecraft.util.Mth;

/**
 * The Lunar trip's curves against the fixation's progress, zero as Lunar takes hold and one as it carries the
 * caster: the sky dims to the star void first, the land drops into darkness behind it, and the moon swells from
 * vanilla's size, slowly and then rushing in, until it covers the whole view; white floods the view as it does,
 * stands full over the crossing until the caster's client learns they arrived, and then clears. The trip home
 * mirrors it with the blue planet swelling in its phase under lunar's sky, already the void.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 */
final class LunarTrip {

    /** How far off vanilla's sky draws the moon, and the moon quad's half width there. */
    static final float SKY_DISTANCE = 100F;
    /** The share of the moon sprite's width its disc spans: eight of its thirty-two texels. */
    static final double MOON_DISC_SHARE = LunarSky.MOON_DISC_SHARE;
    /** Vanilla's moon disc's half width, as the tangent of its angle from the view's center. */
    static final double RESTING_MOON_HALF_TANGENT = LunarSky.VANILLA_MOON_HALF_WIDTH / SKY_DISTANCE * MOON_DISC_SHARE;
    /** The widest view a player holds: vanilla's widest field of view slider on an ultrawide screen. */
    private static final double WIDEST_VERTICAL_FOV_DEGREES = 110.0;
    private static final double WIDEST_ASPECT = 21.0 / 9.0;
    /**
     * The widest view's half diagonal, as a tangent: a body, the moon's disc or the planet's, covers it
     * whatever way it turns once its half width reaches this.
     */
    static final double WIDEST_VIEW_HALF_TANGENT = Math.tan(Math.toRadians(WIDEST_VERTICAL_FOV_DEGREES / 2))
            * Math.hypot(1.0, WIDEST_ASPECT);
    /** How far past the widest view the swollen moon reaches, so its edge never shows at the end. */
    private static final double SWOLLEN_MARGIN = 1.25;
    private static final double SWOLLEN_HALF_TANGENT = WIDEST_VIEW_HALF_TANGENT * SWOLLEN_MARGIN;
    /** The blue planet's half width as lunar's sky draws it, as a tangent. */
    static final double RESTING_PLANET_HALF_TANGENT = LunarSky.PLANET_HALF_SIZE / SKY_DISTANCE;

    /** The share of the fixation over which the sky dims to the star void. */
    private static final float SKY_DIM_START = 0F;
    private static final float SKY_DIM_END = 0.4F;
    /** The share of the fixation over which the land drops into darkness, after the sky begins. */
    private static final float LAND_DARK_START = 0.2F;
    private static final float LAND_DARK_END = 0.6F;
    /** The share of the fixation from which the swell's rush builds, once the sky is dimming. */
    private static final float RUSH_START = 0.25F;
    /**
     * The swell's starting rate: the share of its whole growth, counted as a power of the swollen size over the
     * resting, that a steady creep carries from the hold's first tick, the rush carrying the rest. A tuning value:
     * 0.4 grows the moon by about a tenth in the hold's first quarter second.
     * decision lunar-moon-moves-from-the-first-tick-on-the-slow-ease
     */
    private static final double SWELL_START_RATE = 0.4;
    /** The share of the fixation from which white floods the view, as the moon nears covering it. */
    private static final float WHITE_FLOOD_START = 0.85F;
    /** The share of the rise's ticks a trip let go of early takes to run back. */
    private static final float RUN_BACK_SHARE = 0.4F;
    /** The ticks the white takes to clear once the caster stands at the far end. */
    static final float WHITE_FADE_TICKS = 30F;

    private LunarTrip() {
    }

    /**
     * How far the trip a fixation draws stands at a frame, between its ticks: the share of a held fixation run, or,
     * for one let go of early, the trip running back from where it had reached.
     *
     * @param fixation the fixation
     * @param gameTime the client's game time
     * @param partial  the frame's partial tick
     * @return the trip's progress, zero to one
     */
    static float progress(LunarFixation fixation, long gameTime, float partial) {
        if (fixation.released()) {
            float reached = fixation.progress(fixation.releasedAt());
            return progressRunningBack(reached, fixation.duration(), gameTime - fixation.releasedAt() + partial);
        }
        return Mth.clamp((gameTime - fixation.startedAt() + partial) / fixation.duration(), 0F, 1F);
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
     * The moon disc's half width as the tangent of its angle from the view's center, vanilla's size swelling until
     * it covers the view.
     *
     * @param progress the share of the fixation run, zero to one
     * @return the half width's tangent
     */
    static double moonHalfTangent(float progress) {
        return swollen(progress, RESTING_MOON_HALF_TANGENT);
    }

    /**
     * How far white has flooded the view over the fixation: clear until the moon has nearly covered it, then
     * rising to stand full at the tick Lunar carries the caster.
     *
     * @param progress the share of the fixation run, zero to one
     * @return none to full
     */
    static float whiteBeforeCarry(float progress) {
        return eased(progress, WHITE_FLOOD_START, 1F);
    }

    /**
     * How white the view stands once Lunar has carried the caster: full while the client awaits the arrival
     * mark, so the dimension change shows no cut, then clearing over a short fade once the mark has landed.
     *
     * @param arrived           whether the arrival mark has reached the client
     * @param ticksSinceArrival the ticks since it did
     * @return full to none
     */
    static float whiteAfterCarry(boolean arrived, float ticksSinceArrival) {
        if (!arrived) {
            return 1F;
        }
        return 1F - eased(ticksSinceArrival, 0F, WHITE_FADE_TICKS);
    }

    /**
     * The ticks a trip let go of early takes to run back to nothing: a share of the ticks it took to rise there, so
     * it runs back quicker than it rose.
     * decision holding-right-click-pulls-the-moon-and-early-release-cancels
     *
     * @param reached  the progress the trip had reached as the caster let go
     * @param duration the fixation's ticks
     * @return the run back's ticks
     */
    static float runBackTicks(float reached, int duration) {
        return reached * duration * RUN_BACK_SHARE;
    }

    /**
     * The progress a trip let go of early stands at as it runs back from where it had reached to nothing.
     *
     * @param reached           the progress the trip had reached as the caster let go
     * @param duration          the fixation's ticks
     * @param ticksSinceRelease the ticks since the caster let go
     * @return the progress, falling from the reach to none
     */
    static float progressRunningBack(float reached, int duration, float ticksSinceRelease) {
        float runBack = runBackTicks(reached, duration);
        if (runBack <= 0F) {
            return 0F;
        }
        return reached * (1F - Mth.clamp(ticksSinceRelease / runBack, 0F, 1F));
    }

    /**
     * The blue planet's half width as a tangent, as the swell takes it on the way home: its size in lunar's sky at the
     * hold's start, growing from the first tick as the moon does until it covers the view.
     * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
     * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
     *
     * @param progress the share of the fixation run, zero to one
     * @return the half width's tangent
     */
    static double planetHalfTangent(float progress) {
        return swollen(progress, RESTING_PLANET_HALF_TANGENT);
    }

    /**
     * A body's half width as a tangent over the swell: growing from its resting size on the hold's first tick by an
     * ever larger factor, a steady creep at first and a rush building over it, so it creeps and then rushes in to
     * cover the view.
     * decision lunar-moon-moves-from-the-first-tick-on-the-slow-ease
     *
     * @param progress the share of the fixation run, zero to one
     * @param resting  the body's resting half width, as a tangent
     * @return the half width's tangent
     */
    private static double swollen(float progress, double resting) {
        double creep = Mth.clamp(progress, 0F, 1F);
        float rush = Mth.clamp((progress - RUSH_START) / (1F - RUSH_START), 0F, 1F);
        double swell = SWELL_START_RATE * creep + (1.0 - SWELL_START_RATE) * rush * rush;
        return resting * Math.pow(SWOLLEN_HALF_TANGENT / resting, swell);
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
