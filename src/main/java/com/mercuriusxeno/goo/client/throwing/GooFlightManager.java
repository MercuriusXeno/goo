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
        FLIGHTS.put(id, new GooFlight(start, throwEnd, targetEntityId, payload.targetPos(), type, payload.delivery(),
                travelTicks, grannyArc, gate.orElse(null), hop, payload.travel()));
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
        /** The far leg's peak, past a gate. */
        private final double farPeak;
        /** The gate the flight goes into, null for a flight on the near side. */
        private final @Nullable Gate gate;
        /** Where the flight meets the gate's face and comes out of the partner, null with no gate. */
        private final GateArc.@Nullable Hop hop;
        /** The thrower's travel velocity at the throw, blocks per tick, zero for a beam. */
        private final Vec3 travel;
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
         * @param travel         the thrower's travel velocity at the throw, blocks per tick
         */
        public GooFlight(Vec3 start, Vec3 throwEnd, int targetEntityId, BlockPos targetBlockPos,
                ResourceKey<GooTypeDefinition> gooType, Delivery delivery, int travelTicks, boolean grannyArc,
                @Nullable Gate gate, GateArc.@Nullable Hop hop, Vec3 travel) {
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
            // aim-and-shots-carry-through-linked-gates: each leg through a gate arcs by its own length
            this.peak = this.hop == null ? delivery.peak(start, throwEnd, grannyArc)
                    : delivery.aimsStraight() ? 0 : ThrowArc.basePeak(start.distanceTo(this.hop.facePoint()));
            this.farPeak = this.hop == null ? 0 : farPeakThrough(this.hop);
            this.travel = driftOf(delivery, travel);
            this.ticksElapsed = 0;
        }

        /**
         * A flight to a near-side target, passing no gate, from a thrower
         * travelling at the velocity given.
         *
         * @param start          the hand the flight leaves
         * @param throwEnd       the target at the throw
         * @param targetEntityId the target entity ID, or -1 for block targets
         * @param targetBlockPos the target block
         * @param gooType        the thrown type
         * @param delivery       the thrown ability's delivery
         * @param travelTicks    the ticks the flight takes
         * @param grannyArc      whether the throw is a lob onto a top face
         * @param travel         the thrower's travel velocity at the throw, blocks per tick
         */
        public GooFlight(Vec3 start, Vec3 throwEnd, int targetEntityId,
                          BlockPos targetBlockPos, ResourceKey<GooTypeDefinition> gooType,
                          Delivery delivery, int travelTicks, boolean grannyArc, Vec3 travel) {
            this(start, throwEnd, targetEntityId, targetBlockPos, gooType, delivery, travelTicks, grannyArc, null,
                    null, travel);
        }

        /**
         * The drift a flight carries: the thrower's travel, or none for a
         * beam, whose line runs from the hand to the target.
         *
         * @param delivery the thrown ability's delivery
         * @param travel   the thrower's travel velocity at the throw, blocks per tick
         * @return the travel velocity the flight drifts by
         */
        private static Vec3 driftOf(Delivery delivery, Vec3 travel) {
            return delivery.fliesStraight() ? Vec3.ZERO : travel;
        }

        /**
         * The peak of a flight's far leg through a gate, by the far leg's own
         * length, as the aim line draws it.
         *
         * @param passed the gate's hop
         * @return the peak height in blocks
         */
        private double farPeakThrough(GateArc.Hop passed) {
            if (delivery.aimsStraight()) {
                return 0;
            }
            return grannyArc && delivery.grannyAllowed() ? ThrowArc.lobPeak(passed.partnerPoint(), throwEnd)
                    : ThrowArc.basePeak(passed.partnerPoint().distanceTo(throwEnd));
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

        private float progress(float partialTick) {
            return Math.min(1.0f, (ticksElapsed + partialTick) / travelTicks);
        }

        private double progressAt(double ticks) {
            return Math.clamp(ticks / travelTicks, 0.0, 1.0);
        }

        /**
         * Where the flight stands at a point of its progress: along its arc,
         * or along its two legs where it passes a gate, pushed by the
         * thrower's drift over its opening ticks.
         * thrown-blob-leaves-the-hand-with-a-fading-drift
         *
         * @param t how far along the flight, from 0 to 1
         * @return the position
         */
        private Vec3 along(double t) {
            Vec3 end = getEnd();
            if (hop == null) {
                return ThrowArc.driftedArcPoint(start, end, t, peak, travel, travelTicks);
            }
            return GateArc.at(start, hop, end, peak, farPeak, t)
                    .add(ThrowArc.driftOffset(travel, t * travelTicks, travelTicks));
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
         * The blob's velocity in blocks per tick, drift included: how far its
         * position moves over the next tick, or over the last one where the
         * next steps across a gate from its face to the partner.
         * thrown-blob-trail-takes-the-blobs-velocity
         *
         * @param partialTick the partial tick for interpolation
         * @return the velocity, blocks per tick
         */
        public Vec3 getTickVelocity(float partialTick) {
            double ticks = ticksElapsed + partialTick;
            double now = progressAt(ticks);
            double next = progressAt(ticks + 1);
            if (crossesTheGate(now, next)) {
                return along(now).subtract(along(progressAt(ticks - 1)));
            }
            return along(next).subtract(along(now));
        }

        private boolean crossesTheGate(double from, double to) {
            return hop != null && GateArc.onNearLeg(start, hop, getEnd(), from)
                    != GateArc.onNearLeg(start, hop, getEnd(), to);
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
