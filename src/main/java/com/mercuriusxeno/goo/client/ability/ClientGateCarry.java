package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.GateCarriedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Sets a body the server carried through a linked gate where it came out,
 * at once: the vanilla teleport a client hears for a mob in sight slides it
 * there over a few ticks, through the walls between the gate it went into
 * and the partner it came out of, drawn dark while inside them.
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
        body.snapTo(carry.feet(), carry.yaw(), carry.pitch());
        body.setDeltaMovement(carry.motion());
        CARRIED.put(carry.entityId(), new Carried(carry.feet(), gameTime + STALE_TICKS));
        // A mob shows a few ticks behind the server; set where the server holds it now, it would stand still
        // until the server's next move arrives, so it moves on along its motion meanwhile.
        if (slide != null) {
            slide.interpolateTo(carry.feet().add(carry.motion().scale(LEAD_TICKS)), carry.yaw(), carry.pitch());
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

    /** Ticks a carried body moves on ahead of the server's next word, as vanilla interpolates a mob's move. */
    static final int LEAD_TICKS = 3;
    /** Ticks after a carry the server's word placing the body back at its landing is dropped. */
    private static final int STALE_TICKS = 6;
    /** How near its landing a move places a body to read as placing it back there. */
    private static final double SAME_SPOT = 0.05;

    /** Where each body lately carried came out, and the game time its carry stops reading stale moves. */
    private record Carried(Vec3 landing, long until) {
    }

    private static final Map<Integer, Carried> CARRIED = new HashMap<>();
}
