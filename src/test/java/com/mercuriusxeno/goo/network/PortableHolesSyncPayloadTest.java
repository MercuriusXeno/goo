package com.mercuriusxeno.goo.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The portable holes payload carries each mouth with its owner's stored kinds and counts whole
 * through its stream codec.
 * decision hole-is-an-in-world-portable-hole-for-items
 */
class PortableHolesSyncPayloadTest {

    @Test
    void storedEntriesRoundTripThroughTheStreamCodec() {
        List<PortableHolesSyncPayload.Stored> stored = List.of(
                new PortableHolesSyncPayload.Stored(Identifier.withDefaultNamespace("cobblestone"), 200),
                new PortableHolesSyncPayload.Stored(Identifier.withDefaultNamespace("diamond"), 3));
        PortableHolesSyncPayload sent = new PortableHolesSyncPayload(List.of(
                new PortableHolesSyncPayload.Mouth(new BlockPos(4, 64, -2), Direction.UP, 1200L, stored)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        PortableHolesSyncPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(stored, PortableHolesSyncPayload.STREAM_CODEC.decode(buf).mouths().getFirst().stored());
    }
}
