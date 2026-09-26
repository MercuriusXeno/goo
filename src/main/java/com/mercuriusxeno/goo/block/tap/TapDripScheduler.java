package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.TapHost;
import com.mercuriusxeno.goo.block.IGooReceptacle;
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
 * the server tick the way the blob throw's pending effects are.
 */
public final class TapDripScheduler {

    /**
     * Drips in flight, in the order they left their taps.
     */
    private static final List<PendingDrip> PENDING = new ArrayList<>();

    /**
     * Log: a tap ability's program the tap host refused at load.
     */
    private static final String LOG_PROGRAM_REFUSED = "Tap ability {} refused on the tap landing: {}";

    /**
     * Log: a drip landed, with the running count to check the visual against.
     */
    private static final String LOG_LANDED = "Tap drip from {} landed on {} ({} landed)";

    /**
     * Drips landed since the server started this JVM, for the debug log.
     */
    private static long landedCount;

    private TapDripScheduler() {
    }

    /**
     * Queues a drip to land after its fall.
     *
     * @param drip the drip in flight
     */
    static void enqueue(PendingDrip drip) {
        PENDING.add(drip);
    }

    /**
     * @return a copy of the drips in flight
     */
    public static List<PendingDrip> pending() {
        return List.copyOf(PENDING);
    }

    /**
     * Lands every drip whose arrival tick has come. A drip whose level
     * belongs to a server no longer running is dropped. The client drip
     * particle draws its own splat on reaching the surface, so a landing
     * sends none; it pours into the block below or runs the type's tap ability.
     *
     * @param server the ticking server
     */
    public static void drainArrived(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        int currentTick = server.getTickCount();
        List<PendingDrip> arrived = new ArrayList<>();
        PENDING.removeIf(drip -> {
            if (drip.level().getServer() != server) {
                return true;
            }
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
     * @return drips landed since the JVM started
     */
    public static long landedCount() {
        return landedCount;
    }

    /**
     * Lands a drip on the block entity below it, if any, else on nothing.
     *
     * @param drip the arrived drip
     * @return the programs run
     */
    static int land(PendingDrip drip) {
        return land(drip, receptacleAt(drip.level(), drip.landingPos()), TapDripScheduler::runTapAbility);
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
        if (receptacle != null && receptacle.insertGoo(drip.type(), TapDrip.DRIP_VOLUME) > 0) {
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
     * Runs the type's tap ability program once on a tap host at the landing
     * (decision tap-ability-tagged-program). A type carrying no
     * tap ability lands its drip and nothing further happens: no program
     * loads and nothing logs (decision drip-without-ability).
     *
     * @param drip the arrived drip
     * @return the programs run: one when the type carries a tap ability, else zero
     */
    public static int runTapAbility(PendingDrip drip) {
        AbilityDefinition ability = AbilityRegistry.tapAbilityFor(drip.type());
        if (ability == null) {
            return 0;
        }
        runProgram(ability, new TapHost(drip.level(), drip.landingPos(), drip.face()));
        return 1;
    }

    /**
     * Loads the ability's program for the tap host and runs its one tick; a
     * program the host cannot serve is refused at load and logged.
     *
     * @param ability the tap ability
     * @param host    the tap host at the landing
     */
    private static void runProgram(AbilityDefinition ability, TapHost host) {
        try {
            ProgramBehavior.forHost(ability.behaviors(), HostKind.TAP).tick(host);
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, ability.id(), e.getMessage());
        }
    }

    /**
     * A drip in flight from a tap to its landing.
     *
     * @param level       the level it falls in
     * @param tapPos      the tap it left
     * @param landingPos  the block it lands on
     * @param face        the landing block's face it strikes
     * @param type        the goo type it carries
     * @param arrivalTick the server tick it lands on
     */
    public record PendingDrip(ServerLevel level, BlockPos tapPos, BlockPos landingPos,
                              Direction face, ResourceKey<GooTypeDefinition> type, int arrivalTick) {
    }
}
