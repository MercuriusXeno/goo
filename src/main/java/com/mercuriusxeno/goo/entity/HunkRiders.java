package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carries the riders of Spire's rising stone: at the start of each entity's
 * own tick, after its last position is taken, an entity standing on a
 * climbing hunk's top is lifted onto where the top climbs this tick, so the
 * climb falls inside the rider's interpolation and the rider glides with the
 * stone whichever of them ticks first. A player is lifted by their own
 * client alone, as their client moves them; the server lifts every other rider.
 * decision spire-carries-its-riders-up-with-the-rising-stone
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class HunkRiders {

    /** The climbing hunks on both sides; the integrated server and its client share it, so it is concurrent. */
    private static final Set<BlockHunk> CLIMBING = ConcurrentHashMap.newKeySet();

    private HunkRiders() {
    }

    /**
     * Marks a hunk climbing this tick.
     *
     * @param hunk the hunk
     */
    static void rises(BlockHunk hunk) {
        CLIMBING.add(hunk);
    }

    /**
     * Forgets a hunk that left its level.
     *
     * @param hunk the hunk
     */
    static void forget(BlockHunk hunk) {
        CLIMBING.remove(hunk);
    }

    /**
     * Lifts an entity standing on a climbing hunk at the start of its tick.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        Entity rider = event.getEntity();
        if (!CLIMBING.isEmpty() && mayRide(rider)) {
            liftOnAnyHunk(rider);
        }
    }

    private static boolean mayRide(Entity rider) {
        return !(rider instanceof BlockHunk) && !rider.isSpectator() && movedHere(rider);
    }

    private static void liftOnAnyHunk(Entity rider) {
        for (BlockHunk hunk : List.copyOf(CLIMBING)) {
            if (hunk.isRemoved() || !hunk.climbsAt(hunk.level().getGameTime())) {
                CLIMBING.remove(hunk);
            } else if (hunk.level() == rider.level() && hunk.liftRider(rider)) {
                return;
            }
        }
    }

    /**
     * Whether this side moves the rider: a player's own client moves the
     * player, and the server moves everything else.
     *
     * @param rider the entity
     * @return true where this side lifts it
     */
    static boolean movedHere(Entity rider) {
        boolean player = rider instanceof Player;
        return rider.level().isClientSide() ? player && ((Player) rider).isLocalPlayer() : !player;
    }
}
