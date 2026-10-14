package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.IGooReceptacle;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Holds tap drips in flight until the server tick they land on, drained from
 * the server tick the way the goo throw's pending effects are. Each server
 * holds one, so its drips end with the server
 * (decision type-package-and-per-server-holders).
 */
public final class TapDripScheduler {

    /**
     * Drips in flight, in the order they left their taps.
     */
    private final List<PendingDrip> pending = new ArrayList<>();

    /**
     * Log: a drip landed, with the running count to check the visual against.
     */
    private static final String LOG_LANDED = "Tap drip from {} landed on {} ({} landed)";

    /**
     * Drips landed since the server started, for the debug log.
     */
    private long landedCount;

    /**
     * Queues a drip to land after its fall.
     *
     * @param drip the drip in flight
     */
    public void enqueue(PendingDrip drip) {
        pending.add(drip);
    }

    /**
     * Drops every drip in flight, as a server stop does.
     */
    public void clear() {
        pending.clear();
    }

    /**
     * @return a copy of the drips in flight
     */
    public List<PendingDrip> pending() {
        return List.copyOf(pending);
    }

    /**
     * Lands every drip whose arrival tick has come. The client drip
     * particle draws its own splat on reaching the surface, so a landing
     * sends none; it pours into the block below or runs the type's tap ability.
     *
     * @param server the ticking server
     */
    public void drainArrived(MinecraftServer server) {
        if (pending.isEmpty()) {
            return;
        }
        int currentTick = server.getTickCount();
        List<PendingDrip> arrived = new ArrayList<>();
        pending.removeIf(drip -> {
            if (currentTick < drip.arrivalTick()) {
                return false;
            }
            arrived.add(drip);
            return true;
        });
        for (PendingDrip drip : arrived) {
            landedCount++;
            Goo.LOGGER.debug(LOG_LANDED, drip.tapPos(), drip.landingPos(), landedCount);
            land(drip);
        }
    }

    /**
     * @return drips landed since the server started
     */
    public long landedCount() {
        return landedCount;
    }

    /**
     * Lands a drip on the block entity below it, if any, else on nothing.
     *
     * @param drip the arrived drip
     * @return the programs run
     */
    static int land(PendingDrip drip) {
        return land(drip, receptacleAt(drip.level(), drip.landingPos()), landed -> 0);
    }

    /**
     * Lands a drip: a receptacle keeping any of its goo swallows it and no
     * program runs; a landing keeping none runs the type's tap ability
     * (decision landing-goo-enters-any-holder).
     *
     * @param drip       the arrived drip
     * @param receptacle the block entity the drip landed on, or null when it holds no goo
     * @param tapAbility runs the type's tap ability at the landing, answering the programs run
     * @return the programs run: zero when the receptacle kept the drip
     */
    public static int land(PendingDrip drip, @Nullable IGooReceptacle receptacle,
                           ToIntFunction<PendingDrip> tapAbility) {
        if (receptacle != null && receptacle.insertGoo(drip.type(), drip.volume()) > 0) {
            return 0;
        }
        return tapAbility.applyAsInt(drip);
    }

    /**
     * @param level the level the drip landed in, or null when none is loaded
     * @param pos   the block the drip landed on
     * @return the receptacle standing there, or null when the block holds no goo
     */
    public static @Nullable IGooReceptacle receptacleAt(@Nullable ServerLevel level, BlockPos pos) {
        return level != null && level.getBlockEntity(pos) instanceof IGooReceptacle receptacle ? receptacle : null;
    }

    /**
     * A drip in flight from a tap to its landing.
     *
     * @param level       the level it falls in
     * @param tapPos      the tap it left
     * @param landingPos  the block it lands on
     * @param face        the landing block's face it strikes
     * @param type        the goo type it carries
     * @param volume      the mB it carries
     * @param arrivalTick the server tick it lands on
     */
    public record PendingDrip(ServerLevel level, BlockPos tapPos, BlockPos landingPos,
                              Direction face, ResourceKey<GooTypeDefinition> type, int volume, int arrivalTick) {
    }
}
