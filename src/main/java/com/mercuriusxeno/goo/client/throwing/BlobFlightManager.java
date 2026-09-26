package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ThrowArc;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.network.BlobFlightPayload;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side manager for active blob flights. Tracks each flight from
 * throw to arrival, providing interpolated position each frame for rendering
 * and particle spawning.
 */
public final class BlobFlightManager {

    /**
     * Epsilon for near-zero length detection in direction vectors.
     */
    private static final double DIRECTION_EPSILON = 1e-6;

    /** Active flights, keyed by a monotonically increasing ID. */
    private static final Map<Integer, BlobFlight> FLIGHTS = new ConcurrentHashMap<>();
    /**
     * Velocity finite-difference step size.
     */
    private static final float VELOCITY_DT = 0.01f;
    private static int nextId;

    private BlobFlightManager() {
    }

    /**
     * Adds a new flight from the server's flight broadcast.
     * Called by BlobFlightHandler on packet receipt.
     *
     * @param payload the network payload
     */
    public static void addFlight(BlobFlightPayload payload) {
        ResourceKey<GooTypeDefinition> type = GooTypes.byId(payload.gooTypeId());
        if (type == null) {
            return;
        }

        Vec3 start = new Vec3(payload.startX(), payload.startY(), payload.startZ());
        int targetEntityId = payload.targetEntityId();
        Vec3 throwEnd = targetEntityId < 0
                ? resolveBlockTargetPos(payload) : resolveEntityEndAtThrow(targetEntityId, start);
        int travelTicks = payload.travelTicks();

        boolean grannyArc = payload.grannyArc();
        int id = nextId;
        nextId++;
        FLIGHTS.put(id, new BlobFlight(start, throwEnd, targetEntityId,
                payload.targetPos(), type, travelTicks, grannyArc));
    }

    /**
     * The mob's endpoint when the throw lands on this client, the point the
     * aim line arced to (decision diagnose-then-fix-blob-off-the-line).
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
     * The arc peak a flight flies, from the distance between its start and
     * its endpoint at the throw, as the aim line reads it: glow flies straight.
     *
     * @param start      the flight's start
     * @param throwEnd   the flight's endpoint at the throw
     * @param gooType    the thrown goo type
     * @param grannyArc  whether the throw uses the boosted arc
     * @return the peak height in blocks
     */
    static double peakForFlight(Vec3 start, Vec3 throwEnd, ResourceKey<GooTypeDefinition> gooType,
            boolean grannyArc) {
        if (gooType == GooTypes.GLOW) {
            return 0;
        }
        double distance = start.distanceTo(throwEnd);
        return grannyArc ? ThrowArc.grannyPeak(distance) : ThrowArc.basePeak(distance);
    }

    /**
     * Called each client tick to advance flights and remove arrivals.
     */
    public static void tick() {
        Iterator<Map.Entry<Integer, BlobFlight>> it = FLIGHTS.entrySet().iterator();
        while (it.hasNext()) {
            BlobFlight flight = it.next().getValue();
            flight.ticksElapsed++;
            if (flight.gooType == GooTypes.GLOW) {
                tickGlowFlight(it, flight);
            } else if (flight.ticksElapsed >= flight.travelTicks) {
                fireArrival(flight);
                it.remove();
            }
        }
    }

    /**
     * Glow flights have two phases: extend (head travels) then collapse
     * (tail chases). Effect fires when head arrives; flight removed when
     * tail is consumed.
     *
     * @param it     the iterator for safe removal
     * @param flight the glow flight
     */
    private static void tickGlowFlight(
            Iterator<Map.Entry<Integer, BlobFlight>> it, BlobFlight flight) {
        if (flight.ticksElapsed == flight.travelTicks) {
            fireArrival(flight);
        }
        if (flight.ticksElapsed >= flight.travelTicks + flight.travelTicks) {
            it.remove();
        }
    }

    /**
     * Fires the arrival callback for block-target flights.
     *
     * @param flight the arriving flight
     */
    private static void fireArrival(BlobFlight flight) {
        if (flight.targetEntityId < 0) {
            GloveThrowSender.onFlightArrived(flight.targetBlockPos);
        }
    }

    /**
     * Returns all active flights for rendering.
     *
     * @return the activeFlights
     */
    public static Collection<BlobFlight> getActiveFlights() {
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
     * Resolves block face center from the payload via the shared
     * {@link TargetResult#resolveEndpoint()} method.
     *
     * @param payload the network payload
     * @return the resolved endpoint position
     */
    private static Vec3 resolveBlockTargetPos(BlobFlightPayload payload) {
        BlockPos pos = payload.targetPos();
        Direction face = decodeFace(payload.targetFace());
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null
                && mc.level.getBlockState(pos).getBlock() instanceof GlowCrystalBlock) {
            return TargetResult.glowCrystal(pos, face).resolveEndpoint();
        }
        return TargetResult.block(pos, face).resolveEndpoint();
    }

    /**
     * Decodes a face ordinal from the payload into a Direction, defaulting to UP.
     *
     * @param faceOrdinal the ordinal index from the network payload
     * @return the decoded direction, or UP if out of range
     */
    private static Direction decodeFace(int faceOrdinal) {
        return (faceOrdinal >= 0 && faceOrdinal < Direction.values().length)
                ? Direction.values()[faceOrdinal] : Direction.UP;
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
        return new TargetResult.EntityTarget(e).resolveEndpoint();
    }

    /**
     * Mutable flight state for a single blob in transit.
     */
    public static class BlobFlight {
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
        public final int travelTicks;
        public final boolean grannyArc;

        /**
         * Arc peak, fixed at the throw from start to throwEnd.
         */
        private final double peak;
        public int ticksElapsed;

        public BlobFlight(Vec3 start, Vec3 throwEnd, int targetEntityId,
                          BlockPos targetBlockPos, ResourceKey<GooTypeDefinition> gooType,
                          int travelTicks, boolean grannyArc) {
            this.start = start;
            this.throwEnd = throwEnd;
            this.targetEntityId = targetEntityId;
            this.targetBlockPos = targetBlockPos;
            this.gooType = gooType;
            this.travelTicks = travelTicks;
            this.grannyArc = grannyArc;
            this.peak = peakForFlight(start, throwEnd, gooType, grannyArc);
            this.ticksElapsed = 0;
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
            float t = Math.min(1.0f, (ticksElapsed + partialTick) / travelTicks);
            return ThrowArc.arcPoint(start, getEnd(), t, peak);
        }

        /**
         * Returns normalized velocity direction for tail orientation.
         *
         * @param partialTick the partial tick for interpolation
         * @return the velocity
         */
        public Vec3 getVelocity(float partialTick) {
            float t = Math.min(1.0f, (ticksElapsed + partialTick) / travelTicks);
            Vec3 end = getEnd();
            Vec3 posNow = ThrowArc.arcPoint(start, end, t, peak);
            Vec3 posNext = ThrowArc.arcPoint(start, end,
                    Math.min(1.0, t + VELOCITY_DT), peak);
            Vec3 diff = posNext.subtract(posNow);
            double len = diff.length();
            return len > DIRECTION_EPSILON ? diff.scale(1.0 / len) : new Vec3(0, 1, 0);
        }
    }
}
