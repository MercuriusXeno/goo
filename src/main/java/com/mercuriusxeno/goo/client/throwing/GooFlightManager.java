package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.GateRay;
import com.mercuriusxeno.goo.client.ability.SignalRings;
import com.mercuriusxeno.goo.client.ability.ZapBolts;
import com.mercuriusxeno.goo.network.GooFlightPayload;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.throwing.GateArc;
import com.mercuriusxeno.goo.throwing.ThrowArc;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side manager for active goo flights. Tracks each flight from
 * throw to arrival, providing interpolated position each frame for rendering
 * and particle spawning.
 */
public final class GooFlightManager {

    /**
     * Epsilon for near-zero length detection in direction vectors.
     */
    private static final double DIRECTION_EPSILON = 1e-6;

    /** Active flights, keyed by a monotonically increasing ID. */
    private static final Map<Integer, GooFlight> FLIGHTS = new ConcurrentHashMap<>();
    /**
     * Velocity finite-difference step size.
     */
    private static final float VELOCITY_DT = 0.01f;
    private static int nextId;

    private GooFlightManager() {
    }

    /**
     * Adds a new flight from the server's flight broadcast.
     * Called by GooFlightHandler on packet receipt.
     *
     * @param payload the network payload
     */
    public static void addFlight(GooFlightPayload payload) {
        ResourceKey<GooTypeDefinition> type = GooTypes.byId(payload.gooTypeId());
        if (type == null) {
            return;
        }

        Vec3 start = new Vec3(payload.startX(), payload.startY(), payload.startZ());
        int targetEntityId = payload.targetEntityId();
        Vec3 throwEnd = targetEntityId < 0
                ? payload.targetPoint() : resolveEntityEndAtThrow(targetEntityId, start);
        if (ZapBolts.strikesAsLightning(type, payload.delivery())) {
            // zap-ticks-the-device-and-stuns
            ZapBolts.strike(start, throwEnd);
            Direction face = GooThrowHandler.directionFromOrdinal(payload.targetFace());
            if (targetEntityId < 0 && face != null) {
                // zap-disperses-into-signal
                SignalRings.disperseAt(payload.targetPos(), face, payload.abilityId());
            }
            return;
        }
        int travelTicks = payload.travelTicks();

        boolean grannyArc = payload.grannyArc();
        int id = nextId;
        nextId++;
        // aim-and-shots-carry-through-linked-gates: a flight through a gate flies out of the partner the client holds
        Optional<Gate> gate = payload.gate();
        GateArc.Hop hop = gate.flatMap(passed -> GateRay.hopThrough(GatePassage.clientPairs(), passed,
                payload.facePoint())).orElse(null);
        FLIGHTS.put(id, new GooFlight(start, throwEnd, targetEntityId,
                payload.targetPos(), type, payload.delivery(), travelTicks, grannyArc, gate.orElse(null), hop));
    }

    /**
     * The mob's endpoint when the throw lands on this client, the point the
     * aim line arced to (decision diagnose-then-fix-goo-off-the-line).
     *
     * @param entityId the target entity's id
     * @param start    the flight's start, standing in when the entity is not loaded
     * @return the entity's bounding-box center, or the start
     */
    private static Vec3 resolveEntityEndAtThrow(int entityId, Vec3 start) {
        Vec3 end = resolveEntityPos(entityId);
        return end == null ? start : end;
    }

    /**
     * Called each client tick to advance flights and remove arrivals.
     */
    public static void tick() {
        Iterator<Map.Entry<Integer, GooFlight>> it = FLIGHTS.entrySet().iterator();
        while (it.hasNext()) {
            GooFlight flight = it.next().getValue();
            flight.ticksElapsed++;
            if (flight.delivery.fliesStraight()) {
                tickBeamFlight(it, flight);
            } else if (flight.ticksElapsed >= flight.travelTicks) {
                it.remove();
            }
        }
    }

    /**
     * Beam flights have two phases: extend (head travels) then collapse
     * (tail chases). Effect fires when head arrives; flight removed when
     * tail is consumed.
     *
     * @param it     the iterator for safe removal
     * @param flight the beam flight
     */
    private static void tickBeamFlight(
            Iterator<Map.Entry<Integer, GooFlight>> it, GooFlight flight) {
        if (flight.ticksElapsed >= flight.travelTicks + flight.travelTicks) {
            it.remove();
        }
    }

    /**
     * Returns all active flights for rendering.
     *
     * @return the activeFlights
     */
    public static Collection<GooFlight> getActiveFlights() {
        return FLIGHTS.values();
    }

    /**
     * Clears all flights (on disconnect or dimension change).
     */
    public static void clear() {
        FLIGHTS.clear();
        nextId = 0;
    }

    /**
     * Resolves an entity's bounding-box center, the endpoint the aim line
     * reads, or null if gone.
     *
     * @param entityId the entityId identifier
     * @return the resolved result, or null if unresolvable
     */
    private static @Nullable Vec3 resolveEntityPos(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Entity e = mc.level.getEntity(entityId);
        if (e == null) {
            return null;
        }
        return e.getBoundingBox().getCenter();
    }

    /**
     * Mutable flight state for a single goo in transit.
     */
    public static class GooFlight {
        public final Vec3 start;
        /**
         * End position at the throw: the block face for a block target, the
         * mob's center for an entity target, where a gone mob's flight lands.
         */
        public final Vec3 throwEnd;
        /**
         * Target entity ID, or -1 for block targets.
         */
        public final int targetEntityId;
        /**
         * Target block position for in-flight tracking.
         */
        public final BlockPos targetBlockPos;
        public final ResourceKey<GooTypeDefinition> gooType;
        /**
         * The thrown ability's delivery, which decides the flight's shape
         * (decision delivery-block-in-ability-json).
         */
        public final Delivery delivery;
        public final int travelTicks;
        public final boolean grannyArc;

        /**
         * Arc peak, fixed at the throw from start to throwEnd.
         */
        private final double peak;
        /** The gate the flight goes into, null for a flight on the near side. */
        private final @Nullable Gate gate;
        /** Where the flight meets the gate's face and comes out of the partner, null with no gate. */
        private final GateArc.@Nullable Hop hop;
        public int ticksElapsed;

        /**
         * A flight that passes a linked gate on its way to a far-side target.
         *
         * @param start          the hand the flight leaves
         * @param throwEnd       the target at the throw
         * @param targetEntityId the target entity ID, or -1 for block targets
         * @param targetBlockPos the target block
         * @param gooType        the thrown type
         * @param delivery       the thrown ability's delivery
         * @param travelTicks    the ticks the flight takes
         * @param grannyArc      whether the throw is a lob onto a top face
         * @param gate           the gate the flight goes into
         * @param hop            where it meets the gate's face and comes out of the partner
         */
        public GooFlight(Vec3 start, Vec3 throwEnd, int targetEntityId, BlockPos targetBlockPos,
                ResourceKey<GooTypeDefinition> gooType, Delivery delivery, int travelTicks, boolean grannyArc,
                @Nullable Gate gate, GateArc.@Nullable Hop hop) {
            this.start = start;
            this.throwEnd = throwEnd;
            this.targetEntityId = targetEntityId;
            this.targetBlockPos = targetBlockPos;
            this.gooType = gooType;
            this.delivery = delivery;
            this.travelTicks = travelTicks;
            this.grannyArc = grannyArc;
            this.gate = hop == null ? null : gate;
            this.hop = gate == null ? null : hop;
            this.peak = this.hop == null ? delivery.peak(start, throwEnd, grannyArc) : peakThrough(this.hop);
            this.ticksElapsed = 0;
        }

        /**
         * A flight to a near-side target, passing no gate.
         *
         * @param start          the hand the flight leaves
         * @param throwEnd       the target at the throw
         * @param targetEntityId the target entity ID, or -1 for block targets
         * @param targetBlockPos the target block
         * @param gooType        the thrown type
         * @param delivery       the thrown ability's delivery
         * @param travelTicks    the ticks the flight takes
         * @param grannyArc      whether the throw is a lob onto a top face
         */
        public GooFlight(Vec3 start, Vec3 throwEnd, int targetEntityId,
                          BlockPos targetBlockPos, ResourceKey<GooTypeDefinition> gooType,
                          Delivery delivery, int travelTicks, boolean grannyArc) {
            this(start, throwEnd, targetEntityId, targetBlockPos, gooType, delivery, travelTicks, grannyArc, null,
                    null);
        }

        /**
         * The peak of a flight through a gate, over its two legs' summed
         * length, as the aim line draws it.
         *
         * @param passed the gate's hop
         * @return the peak height in blocks
         */
        private double peakThrough(GateArc.Hop passed) {
            if (delivery.aimsStraight()) {
                return 0;
            }
            return grannyArc && delivery.grannyAllowed() ? ThrowArc.lobPeak(passed.partnerPoint(), throwEnd)
                    : ThrowArc.basePeak(start.distanceTo(passed.facePoint())
                            + passed.partnerPoint().distanceTo(throwEnd));
        }

        /**
         * The gate whose view draws the flight where it stands, past the
         * partner on its far leg.
         *
         * @param partialTick the partial tick for interpolation
         * @return the gate the flight went into, empty on its near leg or with no gate
         */
        public Optional<Gate> drawnThrough(float partialTick) {
            if (gate == null || hop == null || GateArc.onNearLeg(start, hop, getEnd(), progress(partialTick))) {
                return Optional.empty();
            }
            return Optional.of(gate);
        }

        /**
         * Where the flight meets the gate's face and comes out of the partner.
         *
         * @return the hop, null for a flight on the near side
         */
        public GateArc.@Nullable Hop hop() {
            return hop;
        }

        private float progress(float partialTick) {
            return Math.min(1.0f, (ticksElapsed + partialTick) / travelTicks);
        }

        /**
         * Where the flight stands at a point of its progress: along its arc,
         * or along its two legs where it passes a gate.
         *
         * @param t how far along the flight, from 0 to 1
         * @return the position
         */
        private Vec3 along(double t) {
            Vec3 end = getEnd();
            return hop == null ? ThrowArc.arcPoint(start, end, t, peak) : GateArc.at(start, hop, end, peak, t);
        }

        /**
         * Returns the current target position, tracking entity movement live.
         *
         * @return the end
         */
        public Vec3 getEnd() {
            if (targetEntityId >= 0) {
                Vec3 live = resolveEntityPos(targetEntityId);
                if (live != null) {
                    return live;
                }
            }
            return throwEnd;
        }

        /**
         * Returns interpolated position at the given partial tick.
         *
         * @param partialTick the partial tick for interpolation
         * @return the position
         */
        public Vec3 getPosition(float partialTick) {
            return along(progress(partialTick));
        }

        /**
         * Returns normalized velocity direction for tail orientation.
         *
         * @param partialTick the partial tick for interpolation
         * @return the velocity
         */
        public Vec3 getVelocity(float partialTick) {
            float t = progress(partialTick);
            Vec3 posNow = along(t);
            Vec3 posNext = along(Math.min(1.0, t + VELOCITY_DT));
            if (hop != null && GateArc.onNearLeg(start, hop, getEnd(), t)
                    != GateArc.onNearLeg(start, hop, getEnd(), Math.min(1.0, t + VELOCITY_DT))) {
                // A step across the gate jumps from the face to the partner; the near leg's own heading stands.
                posNow = along(Math.max(0.0, t - VELOCITY_DT));
                posNext = along(t);
            }
            Vec3 diff = posNext.subtract(posNow);
            double len = diff.length();
            return len > DIRECTION_EPSILON ? diff.scale(1.0 / len) : new Vec3(0, 1, 0);
        }
    }
}
