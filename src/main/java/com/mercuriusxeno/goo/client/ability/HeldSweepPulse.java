package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.HeldSweepPace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One held-sweep pulse on the caster's client: its front travels at the
 * steady pace from the last radius the server sent, on to its range even
 * after the hold lets go, and lights each wall it passes, which fades over
 * the linger behind it.
 * decision held-sweeps-share-one-fast-shell-visual
 */
final class HeldSweepPulse {

    private static final double TICKS_A_SECOND = 20;
    private static final double BLOCKS_A_TICK = HeldSweepPace.BLOCKS_A_SECOND / TICKS_A_SECOND;

    private final EchoField field;
    private final double range;
    private final int tintRgb;
    private final boolean paintsMobs;
    private final LitWalls walls;
    private final long order;
    /** The walls this front lit, oldest hit first, so the walls whose glow ran out leave from the front. */
    private final Deque<LitFace> lit = new ArrayDeque<>();
    private double frontBefore;
    private double lastRadius;
    private double lastRadiusAt;
    private double front;
    private int nextFace;

    /**
     * A pulse whose walls draw on their own, apart from every other pulse's.
     *
     * @param field   the walls around the spot the pulse left from
     * @param range   the radius its front reaches
     * @param tintRgb its color, 0xRRGGBB
     */
    HeldSweepPulse(EchoField field, double range, int tintRgb) {
        this(field, range, tintRgb, false, new LitWalls());
    }

    /**
     * @param field      the walls around the spot the pulse left from
     * @param range      the radius its front reaches
     * @param tintRgb    its color, 0xRRGGBB
     * @param paintsMobs whether its front outlines the mobs it crosses
     * @param walls      the walls every live pulse holds lit, which this pulse's hits join
     */
    HeldSweepPulse(EchoField field, double range, int tintRgb, boolean paintsMobs, LitWalls walls) {
        this.field = field;
        this.range = range;
        this.tintRgb = tintRgb;
        this.paintsMobs = paintsMobs;
        this.walls = walls;
        this.order = walls.nextOrder();
    }

    /**
     * Takes the radius the server sent for the pulse.
     *
     * @param radius the front's radius on the tick
     * @param at     the game time it arrived
     */
    void heard(double radius, long at) {
        lastRadius = radius;
        lastRadiusAt = at;
    }

    /**
     * The front's radius at a moment: carried at the steady pace from the
     * last radius heard, which it reaches a tick after hearing it, so a
     * frame sweeps it smoothly, and a let-go front runs on to its range;
     * never less than a radius it already drew.
     *
     * @param now the game time with its partial tick
     * @return the radius to draw
     */
    double drawnRadius(double now) {
        double steady = lastRadius + (now - lastRadiusAt - 1) * BLOCKS_A_TICK;
        front = Math.max(front, Math.clamp(steady, 0, range));
        return front;
    }

    /**
     * Moves the front on to a moment, lighting each wall it passes while
     * there is room, and lets the walls whose glow has run out go.
     *
     * @param now     the game time with its partial tick
     * @param isAir   whether a position holds air
     * @param shapeOf a block's voxel shape boxes, block-local
     * @param room    how many more walls may light across every live pulse
     */
    void advance(double now, Predicate<BlockPos> isAir, Function<BlockPos, List<AABB>> shapeOf, int room) {
        frontBefore = front;
        double radius = drawnRadius(now);
        field.reachTo(radius, isAir, shapeOf);
        int left = room;
        while (nextFace < field.size() && field.face(nextFace).distance() <= radius) {
            if (left > 0) {
                LitFace hit = new LitFace(field.face(nextFace), now, this);
                lit.add(hit);
                walls.lit(hit);
                left--;
            }
            nextFace++;
        }
        fadeOut(now);
    }

    /**
     * Lets go every wall whose glow has run out.
     *
     * @param now the game time with its partial tick
     */
    void fadeOut(double now) {
        while (!lit.isEmpty() && HeldSweepVisual.glowAt(now - lit.getFirst().hitAt()) <= 0f) {
            walls.faded(lit.removeFirst());
        }
    }

    /**
     * Whether the pulse is done: its front at its range and every wall it lit faded.
     *
     * @param now the game time with its partial tick
     * @return true once nothing of it shows
     */
    boolean finished(double now) {
        return drawnRadius(now) >= range && lit.isEmpty();
    }

    /**
     * Whether the front still travels.
     *
     * @param now the game time with its partial tick
     * @return true until it reaches its range
     */
    boolean travelling(double now) {
        return drawnRadius(now) < range;
    }

    /**
     * The front's radius before its last advance, so what lies between it
     * and the front now is what the front just crossed.
     *
     * @return the radius before the last advance
     */
    double frontBefore() {
        return frontBefore;
    }

    /**
     * The front's radius as its last advance left it.
     *
     * @return the radius
     */
    double front() {
        return front;
    }

    boolean paintsMobs() {
        return paintsMobs;
    }

    EchoField field() {
        return field;
    }

    int tintRgb() {
        return tintRgb;
    }

    /**
     * Where the pulse stands among every pulse the client made: an older
     * pulse wins a tie between two equally bright hits on one wall.
     *
     * @return the order it was made in, lower for older
     */
    long order() {
        return order;
    }

    Collection<LitFace> lit() {
        return lit;
    }

    /**
     * A wall a front hit, when, and the pulse whose front it was.
     *
     * @param face  the wall
     * @param hitAt the game time with its partial tick the front reached it
     * @param by    the pulse whose front hit it
     */
    record LitFace(EchoField.EchoFace face, double hitAt, HeldSweepPulse by) {
    }
}
