package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.TapHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;

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
     * sends none; it runs the type's tap ability.
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
        arrived.forEach(TapDripScheduler::land);
    }

    /**
     * Runs the type's tap ability on a tap host at the landing: each program
     * entry once (decision tap-ability-tagged-program).
     *
     * @param drip the arrived drip
     */
    static void land(PendingDrip drip) {
        AbilityDefinition ability = AbilityRegistry.tapAbilityFor(drip.type());
        if (ability == null) {
            return;
        }
        TapHost host = new TapHost(drip.level(), drip.landingPos(), drip.face(), drip.type());
        for (AbilityDefinition.BehaviorEntry entry : ability.behaviors()) {
            if (ProgramBehavior.TYPE_NAME.equals(entry.type())) {
                runProgram(ability, entry, host);
            }
        }
    }

    /**
     * Loads one program entry for the tap host and runs its one tick; a
     * program the host cannot serve is refused at load and logged.
     *
     * @param ability the tap ability, for the log
     * @param entry   the program entry
     * @param host    the tap host at the landing
     */
    private static void runProgram(AbilityDefinition ability, AbilityDefinition.BehaviorEntry entry, TapHost host) {
        try {
            ProgramBehavior.forHost(entry.steps(), HostKind.TAP).tick(host);
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
