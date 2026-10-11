package com.mercuriusxeno.goo.client.overlay;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The drip splashes of Tick's tap playing on this client: each aeon drip
 * landing plays the channel's marching squares small where it hits, whole
 * as it lands and fading out over a few ticks.
 * tick-drip-splashes-a-small-tick-effect
 */
public final class TickSplashes {

    /** The client's splashes. */
    public static final TickSplashes CLIENT = new TickSplashes();

    /** A splash's width in blocks. */
    static final double SPLASH_SIZE = 0.4;
    /** Game ticks a splash plays. */
    static final int SPLASH_TICKS = 10;

    private final List<Splash> live = new ArrayList<>();

    /**
     * One splash playing.
     *
     * @param at         the world point the drip landed on
     * @param extraTicks the extra ticks the tap's tick gives, which set the squares' march
     * @param startTick  the game time it landed
     */
    public record Splash(Vec3 at, int extraTicks, long startTick) {

        /**
         * How strongly the splash draws: whole as it lands, falling to nothing
         * as it ends.
         *
         * @param gameTime the game time including the partial tick
         * @return 0 to 1
         */
        public float strength(float gameTime) {
            return Math.clamp(1f - (gameTime - startTick) / SPLASH_TICKS, 0f, 1f);
        }

        boolean isOver(long now) {
            return now - startTick >= SPLASH_TICKS;
        }
    }

    /**
     * Starts a splash.
     *
     * @param at         the world point the drip landed on
     * @param extraTicks the extra ticks the tap's tick gives
     * @param now        the game time
     */
    public void splash(Vec3 at, int extraTicks, long now) {
        live.add(new Splash(at, extraTicks, now));
    }

    /**
     * Drops every splash that has ended and answers the rest.
     *
     * @param now the game time
     * @return the splashes still playing, in the order they landed
     */
    public List<Splash> live(long now) {
        live.removeIf(splash -> splash.isOver(now));
        return List.copyOf(live);
    }

    /** Drops every splash, as the client leaves a level. */
    public void clear() {
        live.clear();
    }
}
