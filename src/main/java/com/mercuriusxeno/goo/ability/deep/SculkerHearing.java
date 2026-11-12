package com.mercuriusxeno.goo.ability.deep;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.SculkerHeardPayload;
import com.mercuriusxeno.goo.network.SculkerVibrationPayload;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * A Sculker hears far: while the brew stands the server carries every sound
 * played within render distance to its holder, in the open as inside
 * blocks, past the range vanilla sends sounds over, so its rings and
 * outlines show there. Vanilla still sends the sounds within its own range.
 * The server also forwards every vibration ranked above 5 that goes off
 * within render distance, so the client rings the sound beside it at the
 * vibration's frequency; one ranked 5 or below would ring no stronger than
 * an unranked sound, so it stays on the server.
 * decision sculker-sight-persists-where-echoes-have-touched
 * decision sculker-echo-intensity-follows-vibration-rank
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class SculkerHearing {

    /** The lowest rank a sound rings at under Sculker, as the operator ruled it; a lower rank rings as this. */
    public static final int FLOOR_RANK = 5;
    private static final int BLOCKS_PER_CHUNK = 16;

    private SculkerHearing() {
    }

    /**
     * Carries a sound the server plays to every Sculker in its level that
     * vanilla's range leaves it short of and render distance
     * reaches; a player that never took Sculker is passed over unread, so
     * no attachment is made and synced to it mid-transfer.
     *
     * @param level  the level the sound plays in
     * @param except the player the sound is not sent to, as vanilla leaves it out, or null
     * @param at     where it plays
     * @param range  how far vanilla sends it, in blocks
     * @param sound  the sound's id
     */
    public static void carry(ServerLevel level, @Nullable Entity except, Vec3 at, double range, String sound) {
        double viewReach = viewReach(level);
        for (ServerPlayer player : level.players()) {
            if (player != except && dives(player) && carriesTo(player.position().distanceTo(at), range, viewReach)) {
                sendIfHeard(player, new SculkerHeardPayload(at.x, at.y, at.z, sound));
            }
        }
    }

    /**
     * Forwards a vibration that reached its listeners to every Sculker
     * within render distance of it, where vanilla ranks it above 5; one a
     * listener cancelled, such as a silent Sculker's own step, is never
     * forwarded.
     *
     * @param event the vanilla game event, after every other listener
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onVanillaGameEvent(VanillaGameEvent event) {
        int rank = VibrationSystem.getGameEventFrequency(event.getVanillaEvent());
        if (!forwards(rank) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = event.getEventPosition();
        double viewReach = viewReach(level);
        for (ServerPlayer player : level.players()) {
            if (dives(player) && player.position().distanceTo(at) <= viewReach) {
                sendIfHeard(player, new SculkerVibrationPayload(at.x, at.y, at.z, rank));
            }
        }
    }

    /**
     * Sends a Sculker what it hears, where its connection carries goo's
     * channel for it; a connection without one, as a gametest's mock player
     * holds, would throw inside the event that heard it.
     *
     * @param player  the Sculker
     * @param payload what it hears
     */
    private static void sendIfHeard(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * Whether a vibration is forwarded to a Sculker: only one ranked above
     * the lowest rank a sound rings at.
     *
     * @param rank its vibration frequency, 0 where vanilla ranks it not at all
     * @return true where it lends a sound more than an unranked sound rings at
     */
    static boolean forwards(int rank) {
        return rank > FLOOR_RANK;
    }

    private static boolean dives(ServerPlayer player) {
        return player.hasData(GooAttachments.SCULKER) && SculkerSwim.dives(player);
    }

    private static double viewReach(ServerLevel level) {
        return (double) level.getServer().getPlayerList().getViewDistance() * BLOCKS_PER_CHUNK;
    }

    /**
     * Whether a sound is carried to a Sculker: only past the range vanilla
     * sends it over, and only within render distance.
     *
     * @param distance     how far the sound plays from the Sculker, in blocks
     * @param vanillaRange how far vanilla sends the sound, in blocks
     * @param viewReach    the render distance, in blocks
     * @return true where the server carries the sound to the diver
     */
    static boolean carriesTo(double distance, double vanillaRange, double viewReach) {
        return distance > vanillaRange && distance <= viewReach;
    }
}
