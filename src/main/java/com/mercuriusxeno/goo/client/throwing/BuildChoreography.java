package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.BuildBounds;
import com.mercuriusxeno.goo.ability.BuildFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * The client's hold of a Build cast, the first ability that submits on a
 * second right click: a press pins the footprint's corner, the held drag
 * sizes the footprint, the release fixes it, the pitch then sets the rise,
 * and the next press submits. Nothing reaches the server until that submit,
 * so a cast abandoned at any point leaves the world untouched.
 * decision spire-rips-walls-and-platforms
 * decision build-dimensions-reach-sixteen-by-sixteen-by-sixteen
 * decision build-goes-sideways
 */
public final class BuildChoreography {

    /** Where a Build cast stands. */
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
    /** Below this, the look runs too near the push's line to aim along it. */
    private static final double PARALLEL = 1e-6;
    private static final double HALF = 0.5;

    private Phase phase = Phase.IDLE;
    private @Nullable BlockPos corner;
    private @Nullable BlockPos dragged;
    private Direction face = Direction.UP;
    private float pitchAtRelease;
    private int rise = BuildBounds.MIN_RISE;
    private BuildBounds bounds = BuildBounds.SIXTEEN_CUBED;
    /** True from a submit until the use key comes up, so the held key's repeat pins no new corner. */
    private boolean awaitingKeyUp;

    /**
     * A right click: pins the corner on a cast's first press, submits on a
     * press while the rise is being set, and is ignored while the drag holds.
     *
     * @param pinned the ground cell the look meets, or null where it meets none
     * @param struck the face of that cell the look meets, which the footprint lies on
     * @param reach  the bounds of the ability cast, which a pinning press holds the cast to
     * @return the footprint to submit, present only on the submitting press
     */
    public Optional<BuildFootprint> press(@Nullable BlockPos pinned, Direction struck, BuildBounds reach) {
        if (awaitingKeyUp) {
            return Optional.empty();
        }
        if (phase == Phase.RISING) {
            Optional<BuildFootprint> submitted = footprint();
            cancel();
            awaitingKeyUp = true;
            return submitted;
        }
        if (phase == Phase.IDLE && pinned != null) {
            corner = pinned.immutable();
            dragged = corner;
            bounds = reach;
            face = struck;
            phase = Phase.SIZING;
        }
        return Optional.empty();
    }

    /**
     * Advances the cast one client tick: while sizing, a held key drags the
     * footprint to the ground cell the look meets and a released key fixes
     * it; while rising, the pitch sets the rise of a footprint on the
     * ground, and the look sets how far one on a wall or a ceiling pushes out.
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
                cellUnderLook(corner, face, eye, look).ifPresent(cell -> dragged = cell);
            } else {
                phase = Phase.RISING;
                pitchAtRelease = pitch;
                rise = BuildBounds.MIN_RISE;
            }
        } else if (phase == Phase.RISING) {
            rise = face == Direction.UP ? riseFromPitch(pitchAtRelease, pitch, bounds)
                    : footprint().flatMap(fixed -> pushFromLook(fixed, eye, look, bounds)).orElse(rise);
        }
    }

    /** Drops the live cast. */
    public void cancel() {
        phase = Phase.IDLE;
        corner = null;
        dragged = null;
        rise = BuildBounds.MIN_RISE;
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
    public Optional<BuildFootprint> footprint() {
        if (phase == Phase.IDLE || corner == null || dragged == null) {
            return Optional.empty();
        }
        return Optional.of(bounds.cap(corner, dragged, rise, face));
    }

    /**
     * The rise a pitch sets: the lowest rise at the pitch the footprint was
     * fixed at, one block higher for each step the look climbs above it.
     *
     * @param pitchAtRelease the pitch when the drag released, in degrees
     * @param pitch          the pitch now, in degrees, negative looking up
     * @param bounds         the bounds the rise holds within
     * @return the rise, within the bounds
     */
    public static int riseFromPitch(float pitchAtRelease, float pitch, BuildBounds bounds) {
        int climbed = (int) Math.floor((pitchAtRelease - pitch) / DEGREES_PER_BLOCK);
        return bounds.capRise(BuildBounds.MIN_RISE + (long) climbed);
    }

    /**
     * How far a footprint on a wall or a ceiling pushes out where the look
     * aims: the distance out along the face's normal, from the footprint's
     * middle, of the point on that line the look passes nearest, so the push
     * ends where the player aims.
     * decision build-goes-sideways
     *
     * @param footprint the fixed footprint
     * @param eye       the player's eye
     * @param look      the player's look, a unit vector
     * @param bounds    the bounds the push holds within
     * @return the push, within the bounds, or empty where the look runs along the normal or aims behind the eye
     */
    public static Optional<Integer> pushFromLook(BuildFootprint footprint, Vec3 eye, Vec3 look, BuildBounds bounds) {
        Vec3 normal = Vec3.atLowerCornerOf(footprint.face().getUnitVec3i());
        Vec3 middle = faceMiddle(footprint);
        double along = normal.dot(look);
        double square = 1 - along * along;
        if (square < PARALLEL) {
            return Optional.empty();
        }
        Vec3 toMiddle = middle.subtract(eye);
        double out = (along * look.dot(toMiddle) - normal.dot(toMiddle)) / square;
        double ahead = (look.dot(toMiddle) - along * normal.dot(toMiddle)) / square;
        if (ahead < 0) {
            return Optional.empty();
        }
        return Optional.of(bounds.capRise(Math.round(out)));
    }

    private static Vec3 faceMiddle(BuildFootprint footprint) {
        Vec3 low = Vec3.atLowerCornerOf(footprint.min());
        Vec3 middle = low.add(Vec3.atLowerCornerOf(footprint.max()).add(1, 1, 1)).scale(HALF);
        Direction face = footprint.face();
        Direction.Axis normal = face.getAxis();
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? footprint.max().get(normal) + 1 : footprint.min().get(normal);
        return middle.with(normal, plane);
    }

    /**
     * The cell the look meets in the plane of the face the corner was pinned
     * on: where the look's line crosses that face's layer, on the side the
     * face looks out of.
     * decision build-goes-sideways
     *
     * @param corner the pinned ground cell
     * @param face   the face the corner was pinned on
     * @param eye    the player's eye
     * @param look   the player's look, a unit vector
     * @return the cell, or empty where the look never comes back to that face
     */
    public static Optional<BlockPos> cellUnderLook(BlockPos corner, Direction face, Vec3 eye, Vec3 look) {
        Direction.Axis normal = face.getAxis();
        int outward = face.getAxisDirection().getStep();
        double plane = corner.get(normal) + (outward > 0 ? 1.0 : 0.0);
        if (look.get(normal) * outward >= 0 || (eye.get(normal) - plane) * outward <= 0) {
            return Optional.empty();
        }
        Vec3 hit = eye.add(look.scale((plane - eye.get(normal)) / look.get(normal)));
        int[] cell = new int[Direction.Axis.VALUES.length];
        for (Direction.Axis axis : Direction.Axis.VALUES) {
            cell[axis.ordinal()] = axis == normal ? corner.get(axis) : (int) Math.floor(hit.get(axis));
        }
        return Optional.of(new BlockPos(cell[Direction.Axis.X.ordinal()], cell[Direction.Axis.Y.ordinal()],
                cell[Direction.Axis.Z.ordinal()]));
    }
}
