package com.mercuriusxeno.goo.client.ber;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

/**
 * How open each oculus's lids stand on this client: they open while the
 * viewer looks at the eye and shut again when the look leaves it, easing
 * either way, and an open eye blinks now and then. Purely cosmetic, per
 * viewer.
 * Decision oculus-prism-becomes-a-hovering-eye.
 */
public final class OculusLids {

    /** How far off the eye the viewer's look may stand and still be looking at it, in degrees. */
    static final double LOOKED_AT_DEGREES = 8;
    /** The farthest a viewer may stand and still be looking at the eye, in blocks. */
    static final double LOOKED_AT_RANGE = 64;
    /**
     * The share of what is left of the way open the lids cover each tick as they open: they part most on the first
     * tick and slow as they near open (operator ruling 2026-11-28).
     */
    static final float OPEN_RATE = 0.22f;
    /** The share of what is left of the way shut the lids cover each tick as they close: they shut faster than open. */
    static final float CLOSE_RATE = 0.45f;
    /** How near its target the lids snap to it, so the easing ends. */
    static final float SETTLED = 0.02f;
    /** Ticks between one blink of an open eye and the next. */
    static final int BLINK_PERIOD = 100;
    /** Ticks a blink takes to snap shut. */
    static final int BLINK_SHUT_TICKS = 2;
    /** Ticks a blink takes to ease open again, slower than it shut. */
    static final int BLINK_OPEN_TICKS = 6;
    private static final double LOOKED_AT_COS = Math.cos(Math.toRadians(LOOKED_AT_DEGREES));

    private static final Map<BlockPos, Openness> OPENNESS = new HashMap<>();

    private OculusLids() {
    }

    /**
     * How open an oculus's lids stood at a time.
     *
     * @param open     0 shut to 1 open
     * @param gameTime the game time it stood so
     */
    private record Openness(float open, float gameTime) {
    }

    /**
     * Whether a viewer is looking at an eye.
     *
     * @param camera  the viewer's camera
     * @param forward the viewer's unit look
     * @param eye     the eye's centre
     * @return true while the look rests on the eye
     */
    public static boolean lookedAt(Vec3 camera, Vec3 forward, Vec3 eye) {
        Vec3 toEye = eye.subtract(camera);
        double distance = toEye.length();
        return distance > 0 && distance <= LOOKED_AT_RANGE && toEye.scale(1 / distance).dot(forward) >= LOOKED_AT_COS;
    }

    /**
     * How shut an oculus's lids stand this frame, easing toward open while
     * the viewer looks at it and toward shut otherwise, with a blink now and
     * then while open.
     *
     * @param pos      the oculus's cell
     * @param lookedAt whether the viewer looks at it
     * @param gameTime the game time with the partial tick
     * @return 0 open, 1 shut
     */
    public static float closure(BlockPos pos, boolean lookedAt, float gameTime) {
        Openness before = OPENNESS.getOrDefault(pos, new Openness(0f, gameTime));
        float ticks = Math.max(0f, gameTime - before.gameTime());
        float open = eased(before.open(), lookedAt ? 1f : 0f, ticks);
        OPENNESS.put(pos.immutable(), new Openness(open, gameTime));
        return Math.max(1f - open, blinkClosure(gameTime) * open);
    }

    /**
     * Eases how open the lids stand toward a target over some ticks, covering a share of what is left each tick: the
     * most on the first and less on each after, opening at the open rate and shutting at the faster close rate.
     * operator ruling 2026-11-28: the lids part a lot at first and slow, and open more slowly than they close
     *
     * @param from   how open they stood, 0 to 1
     * @param target how open they ease toward
     * @param ticks  the ticks gone by
     * @return how open they stand now
     */
    static float eased(float from, float target, float ticks) {
        float rate = target > from ? OPEN_RATE : CLOSE_RATE;
        float eased = target + (from - target) * (float) Math.pow(1 - rate, ticks);
        return Math.abs(target - eased) < SETTLED ? target : eased;
    }

    /**
     * How shut a blink holds the lids: snapping shut over its first ticks, then easing open, fast at first and slow
     * at the end, and open the rest of the period.
     * operator ruling 2026-11-28: the eye blinks with a flutter, opening more slowly than it shuts
     *
     * @param gameTime the game time with the partial tick
     * @return 0 open, 1 shut
     */
    static float blinkClosure(float gameTime) {
        float intoBlink = gameTime % BLINK_PERIOD;
        if (intoBlink < BLINK_SHUT_TICKS) {
            return intoBlink / BLINK_SHUT_TICKS;
        }
        float opened = (intoBlink - BLINK_SHUT_TICKS) / BLINK_OPEN_TICKS;
        return opened >= 1f ? 0f : (1f - opened) * (1f - opened);
    }
}
