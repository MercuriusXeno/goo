package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.Optional;

/**
 * Carries a pick in a plexer's known items menu to the server: an item sets that plexer's target,
 * and Cancel, an empty item, clears it.
 * decision right-click-on-the-plexer-opens-the-known-items-menu
 * decision cancel-cell-clears-the-plexer-target
 *
 * @param plexer the position of the plexer the menu opened on
 * @param item   the id of the item picked, empty for Cancel
 */
public record PlexerTargetPickPayload(BlockPos plexer, Optional<Identifier> item) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<PlexerTargetPickPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "plexer_target_pick"));

    /** Stream codec for encoding and decoding the payload. */
    public static final StreamCodec<ByteBuf, PlexerTargetPickPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PlexerTargetPickPayload::plexer,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs::optional), PlexerTargetPickPayload::item,
            PlexerTargetPickPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
