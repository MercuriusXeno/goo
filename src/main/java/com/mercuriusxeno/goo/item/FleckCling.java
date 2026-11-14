package com.mercuriusxeno.goo.item;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The fleck's motion on a face by its type's viscosity, one model with
 * viscosity as its input: a fleck that spent its bounce on a wall or a
 * ceiling clings there, creeps down a wall at a pace that falls as viscosity
 * rises, and releases the face once it creeps off the face's edge, is pulled
 * off it, or its cling runs out, sooner for a runny type; on a floor it rolls
 * on with the speed it has left, damped faster as viscosity rises. No state
 * holds a fleck to a face for good: every cling runs out within
 * {@link #MAX_CLING_TICKS}.
 * decision flecks-move-by-flexible-goo-physics-and-stay-items
 * decision each-goo-type-carries-elasticity-and-viscosity
 */
public final class FleckCling {

    /** The longest any fleck clings, the bound a cling at any viscosity releases within. */
    public static final int MAX_CLING_TICKS = 200;
    /** The viscosity at which a fleck reads half thick, between runny water and set jelly. */
    static final double HALF_THICK_VISCOSITY = 1500;
    /** How fast, in blocks a tick, the runniest fleck creeps down a wall. */
    static final double RUNNIEST_CREEP = 0.05;
    /** The share of its floor speed the runniest fleck keeps each tick. */
    static final double RUNNIEST_FLOOR_KEEP = 0.9;
    /** The share of its floor speed the thickest fleck keeps each tick. */
    static final double THICKEST_FLOOR_KEEP = 0.5;
    /** The push, in blocks a tick, that holds a clinging fleck against its face. */
    static final double PUSH_INTO_FACE = 0.01;
    /** The pull off its face, in blocks a tick, that tears a clinging fleck away. */
    static final double TEARING_PULL = 0.05;
    /** The speed off a face, in blocks a tick, below which a struck fleck has spent its bounce and clings. */
    static final double SPENT_BOUNCE = 0.15;
    /** A cling's length grows with the cube of thickness, so runny types drip off fast and thick ones hang. */
    private static final int CLING_GROWTH_POWER = 3;

    private FleckCling() {
    }

    /**
     * A fleck clinging to a face.
     *
     * @param face  the way from the fleck into the block it clings to
     * @param ticks how long it has clung
     */
    public record Cling(Direction face, int ticks) {
    }

    /**
     * One tick of a cling.
     *
     * @param velocity the fleck's velocity this tick
     * @param cling    the cling it holds after the tick, or null once it released the face
     */
    public record Step(Vec3 velocity, @Nullable Cling cling) {
    }

    /**
     * How thick a viscosity reads, from 0 for none toward 1, never reaching it.
     *
     * @param viscosity the type's viscosity
     * @return the thickness
     */
    static double thickness(int viscosity) {
        double v = Math.max(0, viscosity);
        return v / (v + HALF_THICK_VISCOSITY);
    }

    /**
     * How long a fleck of a viscosity clings before it drips off.
     *
     * @param viscosity the type's viscosity
     * @return the ticks, at most {@link #MAX_CLING_TICKS}
     */
    static int clingTicks(int viscosity) {
        return (int) Math.ceil(MAX_CLING_TICKS * Math.pow(thickness(viscosity), CLING_GROWTH_POWER));
    }

    /**
     * Whether a fleck leaving a wall or a ceiling at a speed has spent its
     * bounce, so it clings rather than rebounds.
     *
     * @param speedOff its speed off the face after the rebound, in blocks a tick
     * @return true when it clings
     */
    public static boolean spentItsBounce(double speedOff) {
        return Math.abs(speedOff) < SPENT_BOUNCE;
    }

    /**
     * Steps a cling one tick: the fleck keeps any pull along its face, is
     * held against it, and creeps down a wall or hangs from a ceiling; it
     * releases the face, falling as it is, once the face no longer holds it,
     * something pulls it off, or its cling runs out.
     *
     * @param cling     the cling
     * @param velocity  the fleck's velocity this tick before the cling acts
     * @param viscosity the type's viscosity
     * @param faceHolds whether the block it clings to still stands behind it
     * @return the tick's velocity and the cling it holds after
     */
    public static Step stepCling(Cling cling, Vec3 velocity, int viscosity, boolean faceHolds) {
        Vec3 into = Vec3.atLowerCornerOf(cling.face().getUnitVec3i());
        double towardFace = velocity.dot(into);
        if (!faceHolds || cling.ticks() >= clingTicks(viscosity) || -towardFace > TEARING_PULL) {
            return new Step(velocity, null);
        }
        Vec3 alongFace = velocity.subtract(into.scale(towardFace));
        double down = cling.face().getAxis().isHorizontal() ? -creepSpeed(viscosity) : 0;
        return new Step(alongFace.with(Direction.Axis.Y, down).add(into.scale(PUSH_INTO_FACE)),
                new Cling(cling.face(), cling.ticks() + 1));
    }

    /**
     * How fast a clinging fleck of a viscosity creeps down a wall.
     *
     * @param viscosity the type's viscosity
     * @return the speed, in blocks a tick, always above zero
     */
    static double creepSpeed(int viscosity) {
        return RUNNIEST_CREEP * (1 - thickness(viscosity));
    }

    /**
     * A fleck rolling and sliding on a floor for a tick, its sideways speed
     * damped by its viscosity.
     *
     * @param velocity  the fleck's velocity
     * @param viscosity the type's viscosity
     * @return its velocity after the tick
     */
    public static Vec3 rollOn(Vec3 velocity, int viscosity) {
        double keep = RUNNIEST_FLOOR_KEEP - (RUNNIEST_FLOOR_KEEP - THICKEST_FLOOR_KEEP) * thickness(viscosity);
        return new Vec3(velocity.x * keep, velocity.y, velocity.z * keep);
    }
}
