package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.pocket.PocketEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * Server-to-client payload: the entries of the Pocket a player has open,
 * sent when it opens and after each store or take
 * (decision pocket-is-a-void-reserving-container-with-scroll-and-search).
 *
 * @param containerId the open pocket menu's id
 * @param slotCost    the void each slot the stored items fill reserves
 * @param entries     the pocket's entries, in shelf order
 */
public record PocketContentsPayload(int containerId, int slotCost, List<PocketEntry> entries)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<PocketContentsPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "pocket_contents"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<RegistryFriendlyByteBuf, PocketContentsPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PocketContentsPayload::containerId,
            ByteBufCodecs.VAR_INT, PocketContentsPayload::slotCost,
            PocketEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), PocketContentsPayload::entries,
            PocketContentsPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
