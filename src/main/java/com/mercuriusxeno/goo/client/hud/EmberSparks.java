package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import net.minecraft.util.RandomSource;
import java.util.ArrayList;
import java.util.List;

/**
 * The ember particles an ember heart sheds on the HUD (decision
 * ember-hearts-shed-ember-particles): small embers spawn on the heart's
 * standing halves, rise a few pixels and fade out. Whether a slot spawns an
 * ember on a tick is a roll seeded by the slot and the tick, so the sparks
 * flicker at differing moments across the slots and the HUD needs no
 * bookkeeping: every live spark follows from the ticks within one life.
 */
final class EmberSparks {

    /** Ticks a spark lives from spawn to gone. */
    static final int LIFE_TICKS = 14;
    /** The chance a standing ember slot spawns a spark on a tick. */
    static final float SPAWN_CHANCE = 0.12f;
    /** Pixels a spark rises over its life. */
    static final float RISE_PIXELS = 7f;
    /** The heart sprite's width, which a full ember spans and a half ember covers half of. */
    static final float HEART_WIDTH = 9f;
    private static final float HALF = 2f;
    /** The sprite's inset to the heart's body on each side. */
    private static final float BODY_INSET = 1.5f;
    /** The band of the heart's body, from its top, a spark starts within. */
    private static final float START_TOP = 2f;
    private static final float START_BAND = 4f;
    /** The sideways drift a spark may take over its life, either way. */
    private static final float DRIFT_PIXELS = 1.5f;
    private static final long SLOT_SALT = 0x9E3779B97F4A7C15L;
    private static final long TICK_SALT = 0xC2B2AE3D27D4EB4FL;
    private static final int[] COLORS = {0xFFFFD040, 0xFFFF9020, 0xFFFF6010};
    private static final int OPAQUE = 0xFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int RGB_MASK = 0xFFFFFF;

    private EmberSparks() {
    }

    /**
     * One spark as the HUD draws it, relative to the heart's top-left.
     *
     * @param x     the spark's left, in pixels from the heart's left
     * @param y     the spark's top, in pixels from the heart's top; negative above it
     * @param alpha the spark's opacity, one when spawned and zero when gone
     * @param color the spark's colour, opaque ARGB
     */
    record Spark(float x, float y, float alpha, int color) {

        /**
         * The colour with the spark's opacity applied.
         *
         * @return ARGB
         */
        int argb() {
            int a = Math.round(alpha * OPAQUE);
            return a << ALPHA_SHIFT | color & RGB_MASK;
        }
    }

    /**
     * The sparks standing over a heart slot at a moment.
     *
     * @param slot         the heart slot, from the left
     * @param emberHalves  the halves of ember standing over the real heart, zero to two
     * @param guiTick      the GUI tick
     * @param partialTick  the fraction of the tick elapsed
     * @return the live sparks, empty for a slot with no ember
     */
    static List<Spark> sparks(int slot, int emberHalves, int guiTick, float partialTick) {
        List<Spark> live = new ArrayList<>();
        if (emberHalves <= 0) {
            return live;
        }
        float width = emberHalves >= HeartOverlay.FULL_SHIELD ? HEART_WIDTH : HEART_WIDTH / HALF;
        for (int spawn = guiTick - LIFE_TICKS + 1; spawn <= guiTick; spawn++) {
            RandomSource roll = roll(slot, spawn);
            if (roll.nextFloat() < SPAWN_CHANCE) {
                float age = (guiTick - spawn + partialTick) / LIFE_TICKS;
                live.add(spark(roll, width, Math.min(1f, age)));
            }
        }
        return live;
    }

    /**
     * Answers whether a slot spawns a spark on a tick.
     *
     * @param slot the heart slot
     * @param tick the GUI tick
     * @return true when a spark spawns
     */
    static boolean spawnsAt(int slot, int tick) {
        return roll(slot, tick).nextFloat() < SPAWN_CHANCE;
    }

    private static Spark spark(RandomSource roll, float width, float age) {
        // ember-hearts-shed-ember-particles: embers flicker up and fade off the ember hearts
        float startX = BODY_INSET + roll.nextFloat() * (width - BODY_INSET * HALF);
        float startY = START_TOP + roll.nextFloat() * START_BAND;
        float drift = (roll.nextFloat() * HALF - 1f) * DRIFT_PIXELS;
        int color = COLORS[roll.nextInt(COLORS.length)];
        return new Spark(startX + drift * age, startY - RISE_PIXELS * age, 1f - age, color);
    }

    private static RandomSource roll(int slot, int tick) {
        return RandomSource.create(slot * SLOT_SALT ^ tick * TICK_SALT);
    }
}
