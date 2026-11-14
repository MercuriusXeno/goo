package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Where Bore's blob stands while a bore runs: it lifts by the glove and eases
 * forward from there to hold the drill on the crosshair, the drill's length
 * back from its point on a line the drill's angle off the sight line, toward
 * the glove's side and below the crosshair, where the lifted rest lies off
 * the sight line. For a point nearer than that the blob stands no nearer the
 * eye than its rest: the drill shortens and its angle holds, so the blob
 * slides in toward the sight line. The place is eased in the view's frame,
 * so the blob turns with the view at once and only a point moving deeper or
 * nearer eases it, never snapping it.
 * decision bore-drill-is-two-blocks-held-at-an-angle-off-the-sight-line
 * decision bore-blob-eases-to-place-the-drill-at-the-crosshair
 * decision bore-drill-shortens-for-a-near-block-and-holds-its-angle
 * decision bore-blob-lifts-by-the-glove-and-the-drill-extends-from-that-hand
 */
public final class BoreBlobPlace {

    /** Ticks the eased place takes to close all but a share of 1/e of its way to where it is bound. */
    static final double EASE_TICKS = 3;
    /** How near the rest the eased blob has come home, in blocks, once the bore ends. */
    private static final double HOME = 1e-3;
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final Vec3 WORLD_SOUTH = new Vec3(0, 0, 1);
    private static final double NEARLY_UPRIGHT = 0.999;

    /** Each caster's eased place, in its view's frame, and the time it was eased to. */
    private static final Map<Integer, Eased> EASED = new HashMap<>();

    private BoreBlobPlace() {
    }

    /**
     * An eased place: its offset from the eye along the view's look, right and up, and when.
     *
     * @param inView the offset in the view's frame
     * @param at     the game time it was eased to, with the partial tick
     */
    private record Eased(Vec3 inView, double at) {
    }

    /**
     * The place the blob is bound for: the drill's length back from its point
     * on a line its angle off the look, turned toward the rest's side of the
     * sight line; or, where that stands nearer the eye along the look than
     * the rest, as far along the look as the rest, at the same angle.
     *
     * @param eye          the eye the sight runs from
     * @param look         the unit look
     * @param point        the drill's point, on the sight line
     * @param rest         the blob's lifted rest by the glove
     * @param length       the drill's length, in blocks
     * @param angleDegrees the drill's angle off the sight line, in degrees
     * @return the place
     */
    static Vec3 placeFor(Vec3 eye, Vec3 look, Vec3 point, Vec3 rest, double length, double angleDegrees) {
        double angle = Math.toRadians(angleDegrees);
        double back = length * Math.cos(angle);
        double restAlong = rest.subtract(eye).dot(look);
        double pointAlong = point.subtract(eye).dot(look);
        if (pointAlong - back < restAlong) {
            back = Math.max(0, pointAlong - restAlong);
        }
        return point.subtract(look.scale(back)).add(awayFromTheSight(eye, look, rest).scale(back * Math.tan(angle)));
    }

    /**
     * The unit direction square to the look toward the rest's side of the
     * sight line, or below the look where the rest stands on it.
     *
     * @param eye  the eye
     * @param look the unit look
     * @param rest the blob's lifted rest
     * @return the direction
     */
    private static Vec3 awayFromTheSight(Vec3 eye, Vec3 look, Vec3 rest) {
        Vec3 fromEye = rest.subtract(eye);
        Vec3 off = fromEye.subtract(look.scale(fromEye.dot(look)));
        return off.lengthSqr() > HOME * HOME ? off.normalize() : upOf(look).reverse();
    }

    /**
     * The drawn place eased toward where it is bound over some ticks: it
     * closes the same share of the way each tick, so it moves part of the way
     * each frame and arrives without a jump.
     *
     * @param drawn        where the blob was drawn
     * @param bound        where it is bound
     * @param elapsedTicks the ticks since it was drawn, with the partial tick
     * @return where it is drawn now
     */
    static Vec3 easedToward(Vec3 drawn, Vec3 bound, double elapsedTicks) {
        return drawn.lerp(bound, 1 - Math.exp(-Math.max(0, elapsedTicks) / EASE_TICKS));
    }

    /**
     * Where a caster's blob stands this frame: eased toward its place while
     * the caster runs a bore, and back home to its rest once the bore ends.
     *
     * @param casterId    the caster's entity id
     * @param rest        the blob's lifted rest by the glove this frame
     * @param partialTick the frame's partial tick
     * @param now         the game time, with the frame's partial tick
     * @return the blob's place, the rest for a caster running no bore
     */
    static Vec3 of(int casterId, Vec3 rest, float partialTick, double now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(casterId) instanceof Player caster)) {
            return rest;
        }
        BoreStep bore = LiftedPalmBlob.running(casterId, (long) now) ? boreOf(caster) : null;
        if (bore == null && !EASED.containsKey(casterId)) {
            return rest;
        }
        CameraEye.Sight sight = CameraEye.of(mc, caster, partialTick);
        return easedFor(casterId, sight, rest, boundFor(mc, caster, sight, bore, rest), bore == null, now);
    }

    /**
     * Where a caster's blob is bound this frame: the place that holds the
     * drill on the crosshair while a bore runs, and its rest once it ends.
     *
     * @param mc     the client
     * @param caster the caster
     * @param sight  the sight the bore aims along
     * @param bore   the running bore, whose drill length and angle the place takes, or null once it ends
     * @param rest   the blob's lifted rest by the glove
     * @return the place
     */
    private static Vec3 boundFor(Minecraft mc, Player caster, CameraEye.Sight sight, @Nullable BoreStep bore,
                                 Vec3 rest) {
        if (bore == null) {
            return rest;
        }
        ClientAbility ability = BlobRest.selectedAbility(caster);
        double reach = ability == null ? 0 : ability.delivery().reachingTo(caster.blockInteractionRange()).range();
        Vec3 point = BoreDrill.pointAhead(mc.level, sight, bore, reach);
        return placeFor(sight.eye(), sight.look(), point, rest, bore.shape().length(), bore.shape().angle());
    }

    /**
     * A caster's blob eased toward where it is bound, in the view's frame,
     * remembered for the next frame; once a bore has ended and the blob is
     * home at its rest, the memory is dropped.
     *
     * @param casterId the caster's entity id
     * @param sight    the caster's sight this frame
     * @param rest     the blob's lifted rest by the glove
     * @param bound    where the blob is bound
     * @param homing   whether the bore has ended, so the blob is bound for its rest
     * @param now      the game time, with the frame's partial tick
     * @return the blob's place
     */
    private static Vec3 easedFor(int casterId, CameraEye.Sight sight, Vec3 rest, Vec3 bound, boolean homing,
                                 double now) {
        Eased last = EASED.get(casterId);
        Vec3 boundInView = inView(sight, bound);
        Vec3 eased = last == null ? inView(sight, rest) : easedToward(last.inView(), boundInView, now - last.at());
        if (homing && eased.distanceTo(boundInView) < HOME) {
            EASED.remove(casterId);
            return rest;
        }
        EASED.put(casterId, new Eased(eased, last == null ? now : Math.max(now, last.at())));
        return inWorld(sight, eased);
    }

    private static @Nullable BoreStep boreOf(Player caster) {
        ClientAbility ability = BlobRest.selectedAbility(caster);
        return ability == null ? null : BoreDrill.stepOf(ability).orElse(null);
    }

    /**
     * A world point's offset from a sight's eye along its look, right and up.
     *
     * @param sight the sight
     * @param point the world point
     * @return the offset in the sight's frame
     */
    static Vec3 inView(CameraEye.Sight sight, Vec3 point) {
        Vec3 offset = point.subtract(sight.eye());
        Vec3 up = upOf(sight.look());
        Vec3 right = sight.look().cross(up);
        return new Vec3(offset.dot(sight.look()), offset.dot(right), offset.dot(up));
    }

    /**
     * The world point at an offset in a sight's frame.
     *
     * @param sight  the sight
     * @param inView the offset along its look, right and up
     * @return the world point
     */
    static Vec3 inWorld(CameraEye.Sight sight, Vec3 inView) {
        Vec3 up = upOf(sight.look());
        Vec3 right = sight.look().cross(up);
        return sight.eye().add(sight.look().scale(inView.x)).add(right.scale(inView.y)).add(up.scale(inView.z));
    }

    /**
     * The unit up square to a look, in its upright plane.
     *
     * @param look the unit look
     * @return the up
     */
    private static Vec3 upOf(Vec3 look) {
        Vec3 world = Math.abs(look.dot(WORLD_UP)) > NEARLY_UPRIGHT ? WORLD_SOUTH : WORLD_UP;
        return world.subtract(look.scale(look.dot(world))).normalize();
    }
}
