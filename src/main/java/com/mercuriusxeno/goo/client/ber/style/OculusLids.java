package com.mercuriusxeno.goo.client.ber.style;

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
    /** How much of the way open the lids move each tick. */
    static final float OPEN_PER_TICK = 0.25f;
    /** Ticks between one blink of an open eye and the next. */
    static final int BLINK_PERIOD = 100;
    /** Ticks a blink takes to close and open again. */
    static final int BLINK_TICKS = 6;
    private static final float HALF = 0.5f;
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
        float step = Math.max(0f, gameTime - before.gameTime()) * OPEN_PER_TICK;
        float open = eased(before.open(), lookedAt ? 1f : 0f, step);
        OPENNESS.put(pos.immutable(), new Openness(open, gameTime));
        return Math.max(1f - open, blinkClosure(gameTime) * open);
    }

    /**
     * Moves a value toward a target by at most a step.
     *
     * @param from   the value
     * @param target the target
     * @param step   the most it moves
     * @return the moved value
     */
    static float eased(float from, float target, float step) {
        return from < target ? Math.min(target, from + step) : Math.max(target, from - step);
    }

    /**
     * How shut a blink holds the lids: shut at the middle of each blink, open
     * the rest of the period.
     *
     * @param gameTime the game time with the partial tick
     * @return 0 open, 1 shut
     */
    static float blinkClosure(float gameTime) {
        float intoBlink = gameTime % BLINK_PERIOD;
        float halfBlink = BLINK_TICKS * HALF;
        return intoBlink >= BLINK_TICKS ? 0f : 1f - Math.abs(intoBlink - halfBlink) / halfBlink;
    }
}
