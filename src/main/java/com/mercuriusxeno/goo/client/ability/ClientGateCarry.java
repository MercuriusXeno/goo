package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.ExitTilt;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.PortalTransform;
import com.mercuriusxeno.goo.network.GateCarriedPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a body the server carried through a linked gate on into the gate it
 * went into, then turns it through the pair on the frame its drawn middle
 * reaches that gate's face, so it is seen to go into the one gate and come
 * out of the other unbroken. The client draws a mob a few ticks behind the
 * server, so as the carry arrives the drawn body still stands short of the
 * gate; turned through at once, it stood as far behind the partner's wall,
 * hidden there for several frames.
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
     * Takes a carry the server made: the body slides on toward its landing
     * as seen from the side of the gate it went into, until it is drawn
     * through.
     *
     * @param body     the body
     * @param carry    where and how it came out
     * @param gameTime the client's game time, from which the server's stale word is dropped a while
     */
    static void snap(Entity body, GateCarriedPayload carry, long gameTime) {
        CARRIED.put(carry.entityId(), new Carried(carry.feet(), gameTime + STALE_TICKS));
        InterpolationHandler slide = body.getInterpolation();
        if (slide == null) {
            turnThrough(body, new Crossing(carry.entered(), carry.exit(),
                    Mth.wrapDegrees(carry.yaw() - body.getYRot()), body.getBbHeight(), gameTime), gameTime);
            body.setPos(carry.feet());
            return;
        }
        Deque<Crossing> pending = PENDING.computeIfAbsent(carry.entityId(), id -> new ArrayDeque<>());
        float drawnYaw = body.getYRot() + turnsOf(pending);
        pending.addLast(new Crossing(carry.entered(), carry.exit(), Mth.wrapDegrees(carry.yaw() - drawnYaw),
                body.getBbHeight(), gameTime + GIVE_UP_TICKS));
        retarget(slide, drawnSideOf(carry.entityId(), carry.feet()), drawnYawOf(carry.entityId(), carry.yaw()),
                carry.pitch());
    }

    /**
     * Turns each carried body through its pair on the frame its drawn middle
     * reaches the gate it went into, before the frame is drawn.
     *
     * @param delta the frame's delta tracker
     */
    public static void crossDrawnBodies(DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || PENDING.isEmpty()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(true);
        for (Integer id : List.copyOf(PENDING.keySet())) {
            Entity body = mc.level.getEntity(id);
            if (body == null) {
                PENDING.remove(id);
            } else {
                crossDrawn(body, partial, mc.level.getGameTime());
            }
        }
    }

    /**
     * Turns a carried body through its pair once its drawn middle reaches
     * the face of the gate it went into, or once it has been drawn short of
     * it a while, the server having moved it on some other way.
     *
     * @param body     the body
     * @param partial  the frame's partial tick
     * @param gameTime the client's game time
     */
    static void crossDrawn(Entity body, float partial, long gameTime) {
        Deque<Crossing> pending = PENDING.getOrDefault(body.getId(), new ArrayDeque<>());
        if (pending.isEmpty() || !drawnThrough(body, pending.peekFirst(), partial, gameTime)) {
            return;
        }
        Crossing next = pending.removeFirst();
        if (pending.isEmpty()) {
            PENDING.remove(body.getId());
        }
        InterpolationHandler slide = body.getInterpolation();
        if (slide == null || !slide.hasActiveInterpolation()) {
            turnThrough(body, next, gameTime);
            return;
        }
        Vec3 target = next.through(slide.position());
        turnThrough(body, next, gameTime);
        retarget(slide, target, slide.yRot() + next.turn(), slide.xRot());
    }

    /**
     * Whether a body is due to be drawn through a pair: its drawn middle has
     * reached the face of the gate it went into, or it has been drawn short
     * of it a while.
     *
     * @param body     the body
     * @param next     the pair it is next drawn through
     * @param partial  the frame's partial tick
     * @param gameTime the client's game time
     * @return true once it is due
     */
    private static boolean drawnThrough(Entity body, Crossing next, float partial, long gameTime) {
        Vec3 drawnMiddle = body.getPosition(partial).add(0, next.height() * HALF, 0);
        return next.entered().out(drawnMiddle) <= 0 || gameTime >= next.giveUpAt();
    }

    /**
     * Where a place the server sent for a body is drawn: as seen from the
     * side of each gate it went into that the client has not yet drawn it
     * through.
     *
     * @param entityId the body's id
     * @param position the place the server sent
     * @return the place drawn
     */
    public static Vec3 drawnSideOf(int entityId, Vec3 position) {
        Deque<Crossing> pending = PENDING.get(entityId);
        if (pending == null) {
            return position;
        }
        Vec3 drawn = position;
        List<Crossing> newestFirst = new ArrayList<>(pending);
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            drawn = newestFirst.get(i).back(drawn);
        }
        return drawn;
    }

    /**
     * The facing a facing the server sent for a body is drawn with, turned
     * back through each pair it is not yet drawn through.
     *
     * @param entityId the body's id
     * @param yaw      the facing the server sent
     * @return the facing drawn
     */
    public static float drawnYawOf(int entityId, float yaw) {
        Deque<Crossing> pending = PENDING.get(entityId);
        return pending == null ? yaw : yaw - turnsOf(pending);
    }

    /**
     * @return whether the carry is setting a body's slide itself, already seen from its drawn side
     */
    public static boolean retargeting() {
        return retargeting;
    }

    private static void retarget(InterpolationHandler slide, Vec3 target, float yaw, float pitch) {
        retargeting = true;
        try {
            slide.interpolateTo(target, yaw, pitch);
        } finally {
            retargeting = false;
        }
    }

    private static float turnsOf(Deque<Crossing> pending) {
        float turns = 0;
        for (Crossing crossing : pending) {
            turns += crossing.turn();
        }
        return turns;
    }

    /**
     * Turns a body through a pair where it is drawn, its last tick with it,
     * by its middle and stood upright under it, as the server sets a body it
     * carries, so a body coming out of a floor portal from a wall one is never
     * laid half its height to the side.
     *
     * @param body     the body
     * @param crossing the pair it passes
     * @param gameTime the game time it emerges
     */
    private static void turnThrough(Entity body, Crossing crossing, long gameTime) {
        Vec3 before = crossing.through(new Vec3(body.xo, body.yo, body.zo));
        body.setPos(crossing.through(body.position()));
        body.xo = before.x();
        body.yo = before.y();
        body.zo = before.z();
        body.xOld = before.x();
        body.yOld = before.y();
        body.zOld = before.z();
        turnBy(body, crossing.turn());
        body.setDeltaMovement(crossing.way().direction(body.getDeltaMovement()));
        // bodies-emerge-along-their-travel-and-ease-upright: drawn tilted the way it went in, easing upright
        ClientExitTilts.record(body.getId(), ExitTilt.between(crossing.entered(), crossing.exit()), gameTime);
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
     * Whether to drop a move the server sent placing a body back where a
     * carry just set it, and where so, taking that place as the base the
     * server's later small moves are read from, as the move itself would.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param body     the body
     * @param position where the move places it
     * @param gameTime the client's game time
     * @return true for a stale move to drop
     */
    public static boolean dropStale(Entity body, Vec3 position, long gameTime) {
        if (!staleSync(body.getId(), position, gameTime)) {
            return false;
        }
        // The server reads its later small moves from this place; dropped whole, the client would read them from
        // where the body stood before the carry, the pair's whole distance away.
        body.getPositionCodec().setBase(position);
        return true;
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

    /** Forgets every carry, as a test leaves the client. */
    static void forgetAll() {
        CARRIED.clear();
        PENDING.clear();
    }

    private static final double HALF = 0.5;
    /** Ticks after a carry the server's word placing the body back at its landing is dropped. */
    private static final int STALE_TICKS = 6;
    /** How near its landing a move places a body to read as placing it back there. */
    private static final double SAME_SPOT = 0.05;
    /** About half a second, the ticks a body drawn short of the gate it went into is turned through anyway. */
    private static final int GIVE_UP_TICKS = 10;

    /** Where each body lately carried came out, and the game time its carry stops reading stale moves. */
    private record Carried(Vec3 landing, long until) {
    }

    /**
     * A pair a carried body is not yet drawn through.
     *
     * @param entered  the gate it went into
     * @param exit     the partner it came out of
     * @param turn     how far the pair turns its facing, in degrees
     * @param height   the body's height, its middle the point it turns by
     * @param giveUpAt the game time it is turned through though still drawn short of the gate
     */
    private record Crossing(Gate entered, Gate exit, float turn, double height, long giveUpAt) {

        PortalTransform way() {
            return PortalTransform.between(entered, exit);
        }

        Vec3 through(Vec3 feet) {
            return byMiddle(way(), feet);
        }

        Vec3 back(Vec3 feet) {
            return byMiddle(PortalTransform.between(exit, entered), feet);
        }

        private Vec3 byMiddle(PortalTransform transform, Vec3 feet) {
            Vec3 halfUp = new Vec3(0, height * HALF, 0);
            return transform.point(feet.add(halfUp)).subtract(halfUp);
        }
    }

    private static final Map<Integer, Carried> CARRIED = new HashMap<>();
    private static final Map<Integer, Deque<Crossing>> PENDING = new HashMap<>();
    private static boolean retargeting;
}
