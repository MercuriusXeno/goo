package com.mercuriusxeno.goo.ability.deep;

import com.mercuriusxeno.goo.network.SculkerHeardPayload;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * A Sculker hears far: while the brew stands the server carries every sound
 * played within render distance to its holder, in the open as inside
 * blocks, past the range vanilla sends sounds over, so its rings and
 * outlines show there. Vanilla still sends the sounds within its own range.
 * decision sculker-sight-persists-where-echoes-have-touched
 */
public final class SculkerHearing {

    private static final int BLOCKS_PER_CHUNK = 16;

    private SculkerHearing() {
    }

    /**
     * Carries a sound the server plays to every Sculker in its level that
     * vanilla's range leaves it short of and render distance
     * reaches; a player that never took Sculker is passed over unread, so
     * no attachment is made and synced to it mid-transfer.
     *
     * @param level    the level the sound plays in
     * @param except   the player the sound is not sent to, as vanilla leaves it out, or null
     * @param at       where it plays
     * @param range    how far vanilla sends it, in blocks
     * @param loudness its volume, 1 for an ordinary sound
     */
    public static void carry(ServerLevel level, @Nullable Entity except, Vec3 at, double range, float loudness) {
        double viewReach = (double) level.getServer().getPlayerList().getViewDistance() * BLOCKS_PER_CHUNK;
        for (ServerPlayer player : level.players()) {
            if (player != except && player.hasData(GooAttachments.SCULKER) && SculkerSwim.dives(player)
                    && carriesTo(player.position().distanceTo(at), range, viewReach)) {
                PacketDistributor.sendToPlayer(player, new SculkerHeardPayload(at.x, at.y, at.z, loudness, (float) range));
            }
        }
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
