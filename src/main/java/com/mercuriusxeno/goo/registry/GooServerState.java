package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.ability.frost.GlacialFields;
import com.mercuriusxeno.goo.ability.petrify.BlockExposures;
import com.mercuriusxeno.goo.ability.program.MorphBirths;
import com.mercuriusxeno.goo.block.ability.AbilityBlockFallScheduler;
import com.mercuriusxeno.goo.block.ability.HostFreeCells;
import com.mercuriusxeno.goo.block.ability.HostSpreaders;
import com.mercuriusxeno.goo.block.ability.MoltenHeats;
import com.mercuriusxeno.goo.block.ability.WispClaims;
import com.mercuriusxeno.goo.network.DripFlights;
import com.mercuriusxeno.goo.network.GooEffectScheduler;
import com.mercuriusxeno.goo.network.KnifeHolds;
import com.mercuriusxeno.goo.network.ScanWaves;
import com.mercuriusxeno.goo.network.StreamHolds;
import com.mercuriusxeno.goo.network.ThrownKnives;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * What one server holds between ticks: the goo effects, marker falls and
 * spray drips in flight, and the stream holds. Each server holds its
 * own and a server stop clears it, so nothing in flight lands against a
 * stopped server's levels (decision type-package-and-per-server-holders).
 */
public final class GooServerState {

    private final GooEffectScheduler gooEffects = new GooEffectScheduler();
    private final KnifeHolds knifeHolds = new KnifeHolds();
    private final ThrownKnives thrownKnives = new ThrownKnives();
    private final MorphBirths morphBirths = new MorphBirths();
    private final BlockExposures blockExposures = new BlockExposures();
    private final AbilityBlockFallScheduler markerFalls = new AbilityBlockFallScheduler();
    private final StreamHolds streamHolds = new StreamHolds();
    private final GlacialFields glacialFields = new GlacialFields();
    private final ScanWaves scanWaves = new ScanWaves();
    private final DripFlights dripFlights = new DripFlights();
    private final MoltenHeats moltenHeats = new MoltenHeats();
    private final HostSpreaders hostSpreaders = new HostSpreaders();
    private final HostFreeCells hostFreeCells = new HostFreeCells();
    private final WispClaims wispClaims = new WispClaims();

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
     * Each player's Knives hold and the knives it has formed
     * (decision knives-charge-one-per-three-ticks-up-to-ten).
     *
     * @return the Knives holds
     */
    public KnifeHolds knifeHolds() {
        return knifeHolds;
    }

    /**
     * Knives' thrown knives in flight (decision knives-and-dart-function-as-projectiles).
     *
     * @return the thrown knives
     */
    public ThrownKnives thrownKnives() {
        return thrownKnives;
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
     * The spray drips in flight, each landing where its arc strikes
     * (decision goop-and-slick-coat-lands-where-each-drip-arc-strikes).
     *
     * @return the drip flights
     */
    public DripFlights dripFlights() {
        return dripFlights;
    }

    /**
     * The Molten cells melting the block below them
     * (decision stone-goes-red-hot-before-molten-turns-it-to-lava).
     *
     * @return the molten heats
     */
    public MoltenHeats moltenHeats() {
        return moltenHeats;
    }

    /**
     * The sculk charges Host goo started from deaths near it
     * (decision mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo).
     *
     * @return the Host spreaders
     */
    public HostSpreaders hostSpreaders() {
        return hostSpreaders;
    }

    /**
     * The Host cells a recent walk found free of a stranded charge
     * (decision sculk-charge-runs-along-host-goo-without-weakening).
     *
     * @return the free Host cells
     */
    public HostFreeCells hostFreeCells() {
        return hostFreeCells;
    }

    /**
     * The spots drifting wisps are headed for
     * (decision wisps-travel-as-entities-then-settle-as-glowy-air-blocks).
     *
     * @return the wisp claims
     */
    public WispClaims wispClaims() {
        return wispClaims;
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
        if (gooEffects.hasPending()) {
            gooEffects.drainArrivedEffects(currentTick);
        }
        if (markerFalls.hasPending()) {
            markerFalls.drainArrivedFalls(currentTick);
        }
        if (scanWaves.hasPending()) {
            scanWaves.drainArrived(currentTick);
        }
        if (dripFlights.hasPending()) {
            dripFlights.tick(server);
        }
        morphBirths.bearArrived(currentTick);
        blockExposures.decay(server);
        streamHolds.releaseLapsed(currentTick);
        knifeHolds.tick(currentTick);
        thrownKnives.tick(currentTick);
    }

    /**
     * Drops everything in flight, as a server stop does.
     */
    public void clear() {
        gooEffects.clear();
        knifeHolds.clear();
        thrownKnives.clear();
        blockExposures.clear();
        markerFalls.clear();
        streamHolds.clear();
        morphBirths.clear();
        glacialFields.clear();
        scanWaves.clear();
        dripFlights.clear();
        moltenHeats.clear();
        hostSpreaders.clear();
        hostFreeCells.clear();
        wispClaims.clear();
    }
}
