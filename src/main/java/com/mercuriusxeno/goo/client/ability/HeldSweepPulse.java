package com.mercuriusxeno.goo.client.ability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/**
 * One held-sweep pulse on the caster's client: its front travels at the
 * steady pace the server sent from the last radius the server sent, on to
 * its range even after the hold lets go, and lights each wall it passes,
 * which fades over the linger behind it.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision scry-ring-crosses-its-reach-in-two-seconds
 */
final class HeldSweepPulse {

    private final EchoField field;
    private final double range;
    private final double blocksATick;
    private final int tintRgb;
    private final boolean paintsMobs;
    private final LitWalls walls;
    private final long order;
    /** The walls this front lit out of the caster's sight, oldest hit first, so the walls whose glow ran out leave from the front. */
    private final Deque<LitFace> held = new ArrayDeque<>();
    /** The walls this front lit in the caster's sight, oldest hit first, which fade with the ring. */
    private final Deque<LitFace> ringed = new ArrayDeque<>();
    private double frontBefore;
    private double lastRadius;
    private double lastRadiusAt;
    private double front;
    private int nextFace;

    /**
     * A pulse whose walls draw on their own, apart from every other pulse's.
     *
     * @param field       the walls around the spot the pulse left from
     * @param range       the radius its front reaches
     * @param blocksATick the blocks its front travels each tick
     * @param tintRgb     its color, 0xRRGGBB
     */
    HeldSweepPulse(EchoField field, double range, double blocksATick, int tintRgb) {
        this(field, range, blocksATick, tintRgb, false, new LitWalls());
    }

    /**
     * @param field       the walls around the spot the pulse left from
     * @param range       the radius its front reaches
     * @param blocksATick the blocks its front travels each tick
     * @param tintRgb     its color, 0xRRGGBB
     * @param paintsMobs  whether its front outlines the mobs it crosses
     * @param walls       the walls every live pulse holds lit, which this pulse's hits join
     */
    HeldSweepPulse(EchoField field, double range, double blocksATick, int tintRgb, boolean paintsMobs,
                   LitWalls walls) {
        this.field = field;
        this.range = range;
        this.blocksATick = blocksATick;
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
        double steady = lastRadius + (now - lastRadiusAt - 1) * blocksATick;
        front = Math.max(front, Math.clamp(steady, 0, range));
        return front;
    }

    /**
     * Moves the front on to a moment through a world that names its air
     * sections, lighting each wall it passes while there is room, and lets
     * the walls whose glow has run out go.
     *
     * @param now   the game time with its partial tick
     * @param world the world the front walks
     * @param room  how many more walls may light across every live pulse
     */
    void advance(double now, WallWorld world, int room) {
        advance(now, world, FaceSight.NONE, room);
    }

    /**
     * Moves the front on to a moment through a world, lighting each wall it
     * passes while there is room, each judged once by whether the caster sees
     * it as the front reaches it, and lets the walls whose glow has run out go.
     * decision scry-shows-only-hidden-hollows-and-mobs
     *
     * @param now   the game time with its partial tick
     * @param world the world the front walks
     * @param sight whether the caster sees a wall the front reaches
     * @param room  how many more walls may light across every live pulse
     */
    void advance(double now, WallWorld world, FaceSight sight, int room) {
        frontBefore = front;
        double radius = drawnRadius(now);
        field.reachTo(radius, world);
        field.reachedRange(range);
        int left = room;
        while (nextFace < field.size() && field.face(nextFace).distance() <= radius) {
            if (left > 0) {
                EchoField.EchoFace face = field.face(nextFace);
                LitFace hit = new LitFace(face, now, this, sight.sees(face));
                (hit.inSight() ? ringed : held).add(hit);
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
        fadeOut(held, now);
        fadeOut(ringed, now);
    }

    private void fadeOut(Deque<LitFace> hits, double now) {
        while (!hits.isEmpty() && hits.getFirst().glowAt(now) <= 0f) {
            walls.faded(hits.removeFirst());
        }
    }

    /**
     * Whether the pulse is done: its front at its range and every wall it lit faded.
     *
     * @param now the game time with its partial tick
     * @return true once nothing of it shows
     */
    boolean finished(double now) {
        return drawnRadius(now) >= range && held.isEmpty() && ringed.isEmpty();
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

    /**
     * The walls this front holds lit, in sight and out of it.
     *
     * @return the hits, those out of sight first
     */
    Collection<LitFace> lit() {
        List<LitFace> all = new ArrayList<>(held);
        all.addAll(ringed);
        return all;
    }

    /**
     * @return how many walls this front holds lit
     */
    int litCount() {
        return held.size() + ringed.size();
    }

    /**
     * A wall a front hit, when, the pulse whose front it was, and whether
     * the caster saw it as the front reached it.
     *
     * @param face    the wall
     * @param hitAt   the game time with its partial tick the front reached it
     * @param by      the pulse whose front hit it
     * @param inSight whether the caster saw it, so it takes only the ring
     */
    record LitFace(EchoField.EchoFace face, double hitAt, HeldSweepPulse by, boolean inSight) {

        /**
         * How brightly the hit shows: a wall in sight with the ring alone, a
         * wall out of sight held and then faded.
         * decision scry-visible-blocks-take-only-the-fading-ring
         *
         * @param now the game time with its partial tick
         * @return the glow, 0 to 1
         */
        float glowAt(double now) {
            return inSight ? HeldSweepVisual.ringGlowAt(now - hitAt) : HeldSweepVisual.glowAt(now - hitAt);
        }
    }
}
