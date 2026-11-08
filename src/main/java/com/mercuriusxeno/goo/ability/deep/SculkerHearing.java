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
 * A Sculker submerged in blocks hears through the strata, where sound
 * carries: the server carries every sound played within render distance of
 * a diver whose body is inside solid blocks to that diver, past the range
 * vanilla sends sounds over, so its rings and outlines show there. Vanilla
 * still sends the sounds within its own range, and a diver out in the open
 * hears only those.
 * decision sculker-sight-persists-where-echoes-have-touched
 */
public final class SculkerHearing {

    private static final int BLOCKS_PER_CHUNK = 16;

    private SculkerHearing() {
    }

    /**
     * Carries a sound the server plays to every submerged diver in its
     * level that vanilla's range leaves it short of and render distance
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
            if (player != except && player.hasData(GooAttachments.SCULKER) && SculkerSwim.passesThrough(player)
                    && carriesTo(SculkerSwim.insideSolid(player), player.position().distanceTo(at), range, viewReach)) {
                PacketDistributor.sendToPlayer(player, new SculkerHeardPayload(at.x, at.y, at.z, loudness));
            }
        }
    }

    /**
     * Whether a sound is carried to a diver: only to one submerged in
     * blocks, only past the range vanilla sends it over, and only within
     * render distance.
     *
     * @param submerged    whether the diver's body is inside solid blocks
     * @param distance     how far the sound plays from the diver, in blocks
     * @param vanillaRange how far vanilla sends the sound, in blocks
     * @param viewReach    the render distance, in blocks
     * @return true where the server carries the sound to the diver
     */
    static boolean carriesTo(boolean submerged, double distance, double vanillaRange, double viewReach) {
        return submerged && distance > vanillaRange && distance <= viewReach;
    }
}
