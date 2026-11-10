package com.mercuriusxeno.goo.ability.reap;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.BlockAfterimagePayload;
import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Reap's blades swinging on the server: each tick a blade covers the cells
 * and reaches the bodies its shape stands over, from the half tick before to
 * this one. It reaps and replants every ripe plant it covers, leaving the
 * afterimage of how it stood, and cuts every living body it reaches but its
 * slasher, once a swing however many of the swing's blades reach it, each
 * cut landing through the body's immunity so every swing connects in step
 * with its sound. The blades live a few ticks and are never saved.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class BladeFlights {

    /** The ticks a reaped plant's afterimage takes to fade. */
    static final int AFTERIMAGE_TICKS = 12;
    /** How far past a body's bounds a blade still cuts it, in blocks. */
    static final double CUT_SLACK = 0.15;
    private static final double HALF_TICK = 0.5;
    private static final double BODY_MIDDLE = 0.5;

    private static final Map<ServerLevel, List<Flight>> FLYING = new WeakHashMap<>();

    private BladeFlights() {
    }

    /**
     * A blade swinging and the bodies it has cut.
     *
     * @param blade      the blade
     * @param launchedAt the game tick it swung
     * @param slasherId  the id of the entity swinging, which the blade never cuts
     * @param damage     what its cut deals
     * @param struck     the ids of the bodies its swing has cut, shared by the swing's blades
     */
    private record Flight(ReapBlade blade, long launchedAt, int slasherId, float damage, Set<Integer> struck) {
    }

    /**
     * Whether a blade reaches a body: any of its points stands within the
     * body's bounds, or just past them.
     *
     * @param body    the body's bounds
     * @param samples points through the blade
     * @return true when the blade cuts the body
     */
    public static boolean cuts(AABB body, List<Vec3> samples) {
        AABB reach = body.inflate(CUT_SLACK);
        return samples.stream().anyMatch(reach::contains);
    }

    /**
     * Sets one swing's blades flying, a tick apart, to reap and cut as one
     * attack: they share the bodies they have cut, so a body any of them
     * reaches is cut once for the swing.
     *
     * @param level      the server level
     * @param blades     the swing's blades, in the order they fly
     * @param launchedAt the game tick the swing's first blade flies
     * @param slasherId  the id of the entity swinging
     * @param damage     what the swing's cut deals
     */
    public static void launchSwing(ServerLevel level, List<ReapBlade> blades, long launchedAt, int slasherId,
                                   float damage) {
        List<Flight> flights = FLYING.computeIfAbsent(level, key -> new ArrayList<>());
        Set<Integer> struck = new HashSet<>();
        for (int blade = 0; blade < blades.size(); blade++) {
            flights.add(new Flight(blades.get(blade), launchedAt + blade, slasherId, damage, struck));
        }
    }

    /**
     * Swings each blade one tick further, and drops each that has lived its life.
     *
     * @param event the level tick event
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Flight> flights = FLYING.get(level);
        if (flights == null || flights.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        flights.removeIf(flight -> swingTick(level, flight, now));
    }

    /**
     * Reaps what a blade covers and cuts what it reaches this tick.
     *
     * @param level  the server level
     * @param flight the blade's flight
     * @param now    the game tick
     * @return true once the blade has lived its life
     */
    private static boolean swingTick(ServerLevel level, Flight flight, long now) {
        long age = now - flight.launchedAt();
        if (age < 0) {
            return false;
        }
        List<Vec3> samples = new ArrayList<>(flight.blade().samplesAt(Math.max(0, age - HALF_TICK)));
        samples.addAll(flight.blade().samplesAt(age));
        reapUnder(level, samples);
        cutReached(level, flight, samples);
        return age >= flight.blade().shape().lifeTicks();
    }

    private static void reapUnder(ServerLevel level, List<Vec3> samples) {
        Set<BlockPos> cells = new LinkedHashSet<>();
        samples.forEach(point -> cells.add(BlockPos.containing(point)));
        for (BlockPos cell : cells) {
            BlockState stood = level.getBlockState(cell);
            if (Reaping.reap(level, cell)) {
                BlockVisuals.sendToWatchers(level, cell,
                        new BlockAfterimagePayload(cell, Block.getId(stood), GooTypes.LEAF, AFTERIMAGE_TICKS));
            }
        }
    }

    private static void cutReached(ServerLevel level, Flight flight, List<Vec3> samples) {
        AABB around = new AABB(samples.getFirst(), samples.getFirst());
        for (Vec3 point : samples) {
            around = around.minmax(new AABB(point, point));
        }
        for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, around.inflate(CUT_SLACK),
                body -> body.getId() != flight.slasherId() && body.isAlive())) {
            if (!flight.struck().contains(body.getId()) && cuts(body.getBoundingBox(), samples)) {
                flight.struck().add(body.getId());
                cut(level, body, flight.damage());
            }
        }
    }

    /**
     * Cuts a body with the magic Leaf's toxin hurts with, clearing its
     * immunity on both sides so every blade's cut lands and flashes, and puffs
     * a sweep where the blade connects.
     *
     * @param level  the server level
     * @param body   the body cut
     * @param damage what the cut deals
     */
    private static void cut(ServerLevel level, LivingEntity body, float damage) {
        body.invulnerableTime = 0;
        body.hurtServer(level, level.damageSources().magic(), damage);
        body.invulnerableTime = 0;
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, body.getX(), body.getY() + body.getBbHeight() * BODY_MIDDLE,
                body.getZ(), 1, 0, 0, 0, 0);
    }
}
