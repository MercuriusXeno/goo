package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.ability.frost.GlacialFields;
import com.mercuriusxeno.goo.ability.petrify.BlockExposures;
import com.mercuriusxeno.goo.ability.program.MorphBirths;
import com.mercuriusxeno.goo.block.ability.AbilityBlockFallScheduler;
import com.mercuriusxeno.goo.network.FleckLaunches;
import com.mercuriusxeno.goo.network.GooEffectScheduler;
import com.mercuriusxeno.goo.network.ScanWaves;
import com.mercuriusxeno.goo.network.StreamHolds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * What one server holds between ticks: the goo effects and marker falls in
 * flight, and the stream holds. Each server holds its
 * own and a server stop clears it, so nothing in flight lands against a
 * stopped server's levels (decision type-package-and-per-server-holders).
 */
public final class GooServerState {

    private final GooEffectScheduler gooEffects = new GooEffectScheduler();
    private final FleckLaunches fleckLaunches = new FleckLaunches();
    private final MorphBirths morphBirths = new MorphBirths();
    private final BlockExposures blockExposures = new BlockExposures();
    private final AbilityBlockFallScheduler markerFalls = new AbilityBlockFallScheduler();
    private final StreamHolds streamHolds = new StreamHolds();
    private final GlacialFields glacialFields = new GlacialFields();
    private final ScanWaves scanWaves = new ScanWaves();

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
     * The scan waves yet to reach their landings (decision click-is-a-hit-scan-drawn-as-a-delayed-wave).
     *
     * @return the scan waves
     */
    public ScanWaves scanWaves() {
        return scanWaves;
    }

    /**
     * @return the slung flecks yet to leave the hand
     */
    public FleckLaunches fleckLaunches() {
        return fleckLaunches;
    }

    /**
     * The mobs blobs birth, held back while the blob morphs into them
     * (decision slime-blob-morphs-into-the-slime-it-becomes).
     *
     * @return the held births
     */
    public MorphBirths morphBirths() {
        return morphBirths;
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
     * The glacial prisms standing on the server (decision glacial-prism-holds-the-area-frozen).
     *
     * @return the glacial fields
     */
    public GlacialFields glacialFields() {
        return glacialFields;
    }

    /**
     * @return how long each player has held a stream
     */
    public StreamHolds streamHolds() {
        return streamHolds;
    }

    /**
     * Lands every effect and fall whose arrival tick has come.
     *
     * @param server the ticking server
     */
    public void drainArrived(MinecraftServer server) {
        int currentTick = server.getTickCount();
        if (fleckLaunches.hasPending()) {
            fleckLaunches.drainArrived(currentTick);
        }
        if (gooEffects.hasPending()) {
            gooEffects.drainArrivedEffects(currentTick);
        }
        if (markerFalls.hasPending()) {
            markerFalls.drainArrivedFalls(currentTick);
        }
        if (scanWaves.hasPending()) {
            scanWaves.drainArrived(currentTick);
        }
        morphBirths.bearArrived(currentTick);
        blockExposures.decay(server);
        streamHolds.releaseLapsed(currentTick);
    }

    /**
     * Drops everything in flight, as a server stop does.
     */
    public void clear() {
        gooEffects.clear();
        fleckLaunches.clear();
        blockExposures.clear();
        markerFalls.clear();
        streamHolds.clear();
        morphBirths.clear();
        glacialFields.clear();
        scanWaves.clear();
    }
}
