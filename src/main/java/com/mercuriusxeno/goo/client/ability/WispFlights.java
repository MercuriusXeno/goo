package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.WispFlightPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Radiant's wisps as every client tracking the caster sees them appear:
 * each wisp a tick places leaves the caster's glove and flies to its cell,
 * fast at first and settling as it arrives, so the wisps visibly come from
 * the caster. The wisp's renderer asks here where a wisp in flight stands.
 * decision radiant-wisps-where-light-is-low
 * operator ruling 2026-10-10: each wisp flies out of the glove to its spot, in place of motes off the hand
 */
public final class WispFlights {

    /** Ticks a wisp takes to fly from the glove to its cell. */
    public static final int FLIGHT_TICKS = 10;
    /** Each wisp in flight, by its cell's packed position. */
    private static final Long2ObjectMap<Flight> FLYING = new Long2ObjectOpenHashMap<>();

    private WispFlights() {
    }

    /**
     * Handles a tick's placed wisps on the client thread, flying each out of the caster's glove.
     *
     * @param payload the placed wisps
     * @param context the network context
     */
    public static void onPayload(WispFlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !(mc.level.getEntity(payload.casterId()) instanceof Entity caster)) {
                return;
            }
            long now = mc.level.getGameTime();
            FLYING.values().removeIf(flight -> !flight.flyingAt(now));
            Vec3 glove = GloveHand.of(mc, caster, 1f);
            for (BlockPos cell : payload.cells()) {
                FLYING.put(cell.asLong(), new Flight(glove, now));
            }
        });
    }

    /**
     * Where a wisp stands at a time: along its flight while it flies, at its cell's center otherwise.
     *
     * @param cell the wisp's cell
     * @param time the game clock in ticks, with the frame's part tick
     * @return the wisp's center
     */
    public static Vec3 centerOf(BlockPos cell, float time) {
        Vec3 home = Vec3.atCenterOf(cell);
        Flight flight = FLYING.get(cell.asLong());
        if (flight == null) {
            return home;
        }
        float progress = (time - flight.leftAt()) / FLIGHT_TICKS;
        if (progress >= 1f || progress < 0f) {
            FLYING.remove(cell.asLong());
            return home;
        }
        return alongFlight(flight.glove(), home, progress);
    }

    /**
     * A point along a flight, fast off the glove and slowing into the cell.
     *
     * @param glove    where the flight leaves
     * @param home     where it lands
     * @param progress how far through the flight, zero to one
     * @return the point
     */
    static Vec3 alongFlight(Vec3 glove, Vec3 home, float progress) {
        float left = 1f - Mth.clamp(progress, 0f, 1f);
        return glove.lerp(home, 1f - left * left * left);
    }

    /**
     * A wisp's flight: where it left from and when.
     *
     * @param glove  the glove's point when it left
     * @param leftAt the game tick it left
     */
    private record Flight(Vec3 glove, long leftAt) {

        boolean flyingAt(long now) {
            return now >= leftAt && now - leftAt < FLIGHT_TICKS;
        }
    }
}
