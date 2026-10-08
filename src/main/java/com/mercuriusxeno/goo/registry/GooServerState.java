package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.ability.petrify.BlockExposures;
import com.mercuriusxeno.goo.ability.program.Soups;
import com.mercuriusxeno.goo.ability.program.UnmakeDrops;
import com.mercuriusxeno.goo.block.ability.AbilityBlockFallScheduler;
import com.mercuriusxeno.goo.block.tap.TapDripCounts;
import com.mercuriusxeno.goo.block.tap.TapDripScheduler;
import com.mercuriusxeno.goo.network.GooEffectScheduler;
import com.mercuriusxeno.goo.network.StreamHolds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * What one server holds between ticks: the goo effects, tap drips and marker
 * falls in flight, and the stream holds. Each server holds its
 * own and a server stop clears it, so nothing in flight lands against a
 * stopped server's levels (decision type-package-and-per-server-holders).
 */
public final class GooServerState {

    private final GooEffectScheduler gooEffects = new GooEffectScheduler();
    private final TapDripScheduler tapDrips = new TapDripScheduler();
    private final TapDripCounts tapDripCounts = new TapDripCounts();
    private final UnmakeDrops unmakeDrops = new UnmakeDrops();
    private final Soups soups = new Soups();
    private final BlockExposures blockExposures = new BlockExposures();
    private final AbilityBlockFallScheduler markerFalls = new AbilityBlockFallScheduler();
    private final StreamHolds streamHolds = new StreamHolds();

    /**
     * Answers the state the server holds.
     *
     * @param server the server
     * @return the server's state
     */
    public static GooServerState of(MinecraftServer server) {
        return ((GooServerStateHolder) server).gooServerState();
    }

    /**
     * Answers the state the level's server holds.
     *
     * @param level the level
     * @return the server's state, or null for a client level
     */
    public static @Nullable GooServerState of(Level level) {
        MinecraftServer server = level.getServer();
        return server == null ? null : of(server);
    }

    /**
     * @return the goo effects waiting for their goo to land
     */
    public GooEffectScheduler gooEffects() {
        return gooEffects;
    }

    /**
     * @return the tap drips in flight
     */
    public TapDripScheduler tapDrips() {
        return tapDrips;
    }

    /**
     * The goo unmade blocks and mobs leave, held back while their remains
     * morph (decision unmake-waves-dissolve-by-crucible-cost).
     *
     * @return the held drops
     */
    public UnmakeDrops unmakeDrops() {
        return unmakeDrops;
    }

    /**
     * Returns every player's Unmake soup (decision unmake-waves-dissolve-by-crucible-cost).
     *
     * @return the soups
     */
    public Soups soups() {
        return soups;
    }

    /**
     * The drips each block has taken since its tap ability last acted
     * (decision petrify-drip-calcifies-and-grows-dripstone).
     *
     * @return the drip counts
     */
    public TapDripCounts tapDripCounts() {
        return tapDripCounts;
    }

    /**
     * @return the ability block falls in flight
     */
    public AbilityBlockFallScheduler markerFalls() {
        return markerFalls;
    }

    /**
     * How far each block has gone toward its next calcify rung
     * (decision petrify-stone-encasement-and-calcify-map).
     *
     * @return the block exposures
     */
    public BlockExposures blockExposures() {
        return blockExposures;
    }

    /**
     * @return how long each player has held a stream
     */
    public StreamHolds streamHolds() {
        return streamHolds;
    }

    /**
     * Lands every effect, drip and fall whose arrival tick has come.
     *
     * @param server the ticking server
     */
    public void drainArrived(MinecraftServer server) {
        int currentTick = server.getTickCount();
        if (gooEffects.hasPending()) {
            gooEffects.drainArrivedEffects(currentTick);
        }
        if (markerFalls.hasPending()) {
            markerFalls.drainArrivedFalls(currentTick);
        }
        tapDrips.drainArrived(server);
        soups.tick(server, unmakeDrops);
        unmakeDrops.dropArrived(currentTick);
        blockExposures.decay(server);
    }

    /**
     * Drops everything in flight, as a server stop does.
     */
    public void clear() {
        gooEffects.clear();
        tapDrips.clear();
        tapDripCounts.clear();
        blockExposures.clear();
        markerFalls.clear();
        streamHolds.clear();
        unmakeDrops.clear();
        soups.clear();
    }
}
