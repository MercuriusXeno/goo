package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.SpireFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * The client's hold of a Spire cast, the first ability that submits on a
 * second right click: a press pins the footprint's corner, the held drag
 * sizes the footprint, the release fixes it, the pitch then sets the rise,
 * and the next press submits. Nothing reaches the server until that submit,
 * so a cast abandoned at any point leaves the world untouched.
 * decision spire-rips-walls-and-platforms
 */
public final class SpireChoreography {

    /** Where a Spire cast stands. */
    public enum Phase {
        /** No cast is live; a press pins a corner. */
        IDLE,
        /** Right click is held: the drag sizes the footprint. */
        SIZING,
        /** The footprint is fixed: the pitch sets the rise and a press submits. */
        RISING
    }

    /** Degrees of pitch that raise the rise one block, looking up from the pitch at release. */
    public static final float DEGREES_PER_BLOCK = 6f;

    private Phase phase = Phase.IDLE;
    private @Nullable BlockPos corner;
    private @Nullable BlockPos dragged;
    private float pitchAtRelease;
    private int rise = SpireFootprint.MIN_RISE;
    /** True from a submit until the use key comes up, so the held key's repeat pins no new corner. */
    private boolean awaitingKeyUp;

    /**
     * A right click: pins the corner on a cast's first press, submits on a
     * press while the rise is being set, and is ignored while the drag holds.
     *
     * @param pinned the ground cell the look meets, or null where it meets none
     * @return the footprint to submit, present only on the submitting press
     */
    public Optional<SpireFootprint> press(@Nullable BlockPos pinned) {
        if (awaitingKeyUp) {
            return Optional.empty();
        }
        if (phase == Phase.RISING) {
            Optional<SpireFootprint> submitted = footprint();
            cancel();
            awaitingKeyUp = true;
            return submitted;
        }
        if (phase == Phase.IDLE && pinned != null) {
            corner = pinned.immutable();
            dragged = corner;
            phase = Phase.SIZING;
        }
        return Optional.empty();
    }

    /**
     * Advances the cast one client tick: while sizing, a held key drags the
     * footprint to the ground cell the look meets and a released key fixes
     * it; while rising, the pitch sets the rise.
     *
     * @param useKeyDown whether the use key is held this tick
     * @param eye        the player's eye
     * @param look       the player's look, a unit vector
     * @param pitch      the player's pitch in degrees, negative looking up
     */
    public void tick(boolean useKeyDown, Vec3 eye, Vec3 look, float pitch) {
        if (!useKeyDown) {
            awaitingKeyUp = false;
        }
        if (phase == Phase.SIZING && corner != null) {
            if (useKeyDown) {
                groundCellUnderLook(corner, eye, look).ifPresent(cell -> dragged = cell);
            } else {
                phase = Phase.RISING;
                pitchAtRelease = pitch;
                rise = SpireFootprint.MIN_RISE;
            }
        } else if (phase == Phase.RISING) {
            rise = riseFromPitch(pitchAtRelease, pitch);
        }
    }

    /** Drops the live cast. */
    public void cancel() {
        phase = Phase.IDLE;
        corner = null;
        dragged = null;
        rise = SpireFootprint.MIN_RISE;
    }

    /**
     * @return where the cast stands
     */
    public Phase phase() {
        return phase;
    }

    /**
     * The footprint as the cast stands, capped as the server caps it.
     *
     * @return the footprint, empty while no cast is live
     */
    public Optional<SpireFootprint> footprint() {
        if (phase == Phase.IDLE || corner == null || dragged == null) {
            return Optional.empty();
        }
        return Optional.of(SpireFootprint.capped(corner, dragged, rise));
    }

    /**
     * The rise a pitch sets: the lowest rise at the pitch the footprint was
     * fixed at, one block higher for each step the look climbs above it.
     *
     * @param pitchAtRelease the pitch when the drag released, in degrees
     * @param pitch          the pitch now, in degrees, negative looking up
     * @return the rise, within the caps
     */
    public static int riseFromPitch(float pitchAtRelease, float pitch) {
        int climbed = (int) Math.floor((pitchAtRelease - pitch) / DEGREES_PER_BLOCK);
        return Math.clamp(SpireFootprint.MIN_RISE + (long) climbed, SpireFootprint.MIN_RISE, SpireFootprint.MAX_RISE);
    }

    /**
     * The ground cell the look meets on the corner's level: where the look's
     * line crosses the top face of the corner's layer.
     *
     * @param corner the pinned ground cell
     * @param eye    the player's eye
     * @param look   the player's look, a unit vector
     * @return the cell, or empty where the look never comes down to that face
     */
    public static Optional<BlockPos> groundCellUnderLook(BlockPos corner, Vec3 eye, Vec3 look) {
        double top = corner.getY() + 1.0;
        if (look.y >= 0 || eye.y <= top) {
            return Optional.empty();
        }
        double reach = (top - eye.y) / look.y;
        Vec3 hit = eye.add(look.scale(reach));
        return Optional.of(new BlockPos((int) Math.floor(hit.x), corner.getY(), (int) Math.floor(hit.z)));
    }
}
