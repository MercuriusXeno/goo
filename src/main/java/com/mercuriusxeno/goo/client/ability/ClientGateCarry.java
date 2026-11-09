package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.ExitTilt;
import com.mercuriusxeno.goo.ability.quantum.PortalTransform;
import com.mercuriusxeno.goo.network.GateCarriedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns a body the server carried through a linked gate through the same
 * pair where the client draws it, and slides it on from there to where it
 * came out: the vanilla teleport a client hears for a mob in sight slides it
 * there over a few ticks through the walls between, drawn dark while inside
 * them, and setting it at the landing at once flashes it there from short of
 * the gate it went into.
 * aim-and-shots-carry-through-linked-gates
 */
public final class ClientGateCarry {

    private ClientGateCarry() {
    }

    /**
     * Handles a carry on the client thread.
     *
     * @param payload the carry
     * @param context the network context
     */
    public static void handle(GateCarriedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            Entity body = mc.level == null ? null : mc.level.getEntity(payload.entityId());
            if (body != null) {
                snap(body, payload, mc.level.getGameTime());
            }
        });
    }

    /**
     * Sets a carried body where it came out, moving on along its motion.
     *
     * @param body     the body
     * @param carry    where and how it came out
     * @param gameTime the client's game time, from which the server's stale word is dropped a while
     */
    static void snap(Entity body, GateCarriedPayload carry, long gameTime) {
        InterpolationHandler slide = body.getInterpolation();
        if (slide != null) {
            slide.cancel();
        }
        // A mob shows a few ticks behind the server, short of the gate as the carry arrives; turned through the
        // pair where it is drawn, its last tick with it, it is seen going on out of the partner unbroken.
        // Turned by its middle and stood upright under it, as the server sets a body it carries, so a body coming
        // out of a floor portal from a wall one is never laid half its height to the side of its landing.
        PortalTransform through = PortalTransform.between(carry.entered(), carry.exit());
        Vec3 halfUp = new Vec3(0, body.getBbHeight() * HALF, 0);
        Vec3 before = through.point(new Vec3(body.xo, body.yo, body.zo).add(halfUp)).subtract(halfUp);
        body.setPos(through.point(body.position().add(halfUp)).subtract(halfUp));
        body.xo = before.x();
        body.yo = before.y();
        body.zo = before.z();
        body.xOld = before.x();
        body.yOld = before.y();
        body.zOld = before.z();
        turnBy(body, Mth.wrapDegrees(carry.yaw() - body.getYRot()));
        body.setDeltaMovement(carry.motion());
        // bodies-emerge-along-their-travel-and-ease-upright: drawn tilted the way it went in, easing upright
        ClientExitTilts.record(carry.entityId(), ExitTilt.between(carry.entered(), carry.exit()), gameTime);
        CARRIED.put(carry.entityId(), new Carried(carry.feet(), gameTime + STALE_TICKS));
        if (slide != null) {
            slide.interpolateTo(carry.feet(), carry.yaw(), carry.pitch());
        } else {
            body.setPos(carry.feet());
        }
    }

    /**
     * Turns a body's facing, now and last tick, by the pair's turn, so it
     * comes out of the partner facing the way the pair turns it.
     *
     * @param body the body
     * @param turn the turn, in degrees
     */
    private static void turnBy(Entity body, float turn) {
        body.setYRot(body.getYRot() + turn);
        body.yRotO += turn;
        if (body instanceof LivingEntity living) {
            living.yHeadRot += turn;
            living.yHeadRotO += turn;
            living.yBodyRot += turn;
            living.yBodyRotO += turn;
        }
    }

    /**
     * Whether a move the server sent for a body only places it back where a
     * carry set it, just after the carry: the server's own word of the
     * teleport, which would pull the body back to its landing and stall it
     * there while it moves on.
     *
     * @param entityId the body's id
     * @param position where the move places it
     * @param gameTime the client's game time
     * @return true for a stale move to drop
     */
    public static boolean staleSync(int entityId, Vec3 position, long gameTime) {
        Carried carried = CARRIED.get(entityId);
        if (carried == null) {
            return false;
        }
        if (gameTime > carried.until()) {
            CARRIED.remove(entityId);
            return false;
        }
        return position.distanceTo(carried.landing()) < SAME_SPOT;
    }

    private static final double HALF = 0.5;
    /** Ticks after a carry the server's word placing the body back at its landing is dropped. */
    private static final int STALE_TICKS = 6;
    /** How near its landing a move places a body to read as placing it back there. */
    private static final double SAME_SPOT = 0.05;

    /** Where each body lately carried came out, and the game time its carry stops reading stale moves. */
    private record Carried(Vec3 landing, long until) {
    }

    private static final Map<Integer, Carried> CARRIED = new HashMap<>();
}
