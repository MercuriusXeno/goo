package com.mercuriusxeno.goo.ability.end;

import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import java.util.Optional;

/**
 * The way back a player the End gate carried to the End keeps on them:
 * where they portalled in from, which the gate they summon in the End leads to.
 * decision end-gate-in-the-end-is-free-and-returns-you-where-you-portalled-in
 *
 * @param destination where they portalled in from, empty for a player the gate never carried
 */
public record EndWayBack(Optional<EndDestination> destination) {

    /** A player holding no way back. */
    public static final EndWayBack NONE = new EndWayBack(Optional.empty());

    /** Saves the way back with the player. */
    public static final Codec<EndWayBack> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            EndDestination.CODEC.optionalFieldOf("destination").forGetter(EndWayBack::destination)
    ).apply(inst, EndWayBack::new));

    /** Sends the way back to its player's client, which grays End out by it. */
    public static final StreamCodec<ByteBuf, EndWayBack> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(EndDestination.STREAM_CODEC), EndWayBack::destination, EndWayBack::new);

    /**
     * The way back a player keeps once they change level: none once they
     * leave the End by any way, so a later arrival through a stronghold
     * portal grays End out again.
     * decision end-is-grayed-out-in-the-end-unless-you-came-by-the-ability
     *
     * @param from the level they left
     * @param to   the level they arrive in
     * @return the way back they keep
     */
    public EndWayBack afterChangingDimension(ResourceKey<Level> from, ResourceKey<Level> to) {
        return from == Level.END && to != Level.END ? NONE : this;
    }

    /**
     * The way back a player keeps, read without attaching one: a synced
     * attachment a player holds goes out on each of their teleports whatever
     * its sync predicate answers, which a connection lacking the channel, a
     * gametest's mock player, refuses, so a player the gate never carried
     * holds none.
     *
     * @param player the player
     * @return their way back, none where they hold no attachment
     */
    public static EndWayBack heldBy(IAttachmentHolder player) {
        return player.getExistingData(GooAttachments.END_WAY_BACK).orElse(NONE);
    }

    /**
     * Keeps a way back on a player, removing the attachment where none stands.
     *
     * @param player  the player
     * @param wayBack the way back they keep
     */
    public static void keepOn(IAttachmentHolder player, EndWayBack wayBack) {
        if (wayBack.stands()) {
            player.setData(GooAttachments.END_WAY_BACK, wayBack);
        } else {
            player.removeData(GooAttachments.END_WAY_BACK);
        }
    }

    /**
     * @return whether a way back stands, the only state worth saving
     */
    public boolean stands() {
        return destination.isPresent();
    }
}
