package com.mercuriusxeno.goo.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
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
                new PortableHolesSyncPayload.Mouth(List.of(new BlockPos(4, 64, -2)), Direction.UP, 960L, 1200L,
                        stored, List.of())));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        PortableHolesSyncPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(stored, PortableHolesSyncPayload.STREAM_CODEC.decode(buf).mouths().getFirst().stored());
    }

    /**
     * decision swallowed-item-transitions-into-the-hole-smoothly
     */
    @Test
    void swallowsRoundTripThroughTheStreamCodec() {
        List<PortableHolesSyncPayload.Swallowed> swallowed = List.of(
                new PortableHolesSyncPayload.Swallowed(Identifier.withDefaultNamespace("cobblestone"),
                        new Vec3(4.5, 65.05, -1.5), 1080L),
                new PortableHolesSyncPayload.Swallowed(Identifier.withDefaultNamespace("diamond"),
                        new Vec3(3.25, 65.1, -2.75), 1080L));
        PortableHolesSyncPayload sent = new PortableHolesSyncPayload(List.of(
                new PortableHolesSyncPayload.Mouth(List.of(new BlockPos(4, 64, -2)), Direction.UP, 960L, 1200L,
                        List.of(), swallowed)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        PortableHolesSyncPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(swallowed, PortableHolesSyncPayload.STREAM_CODEC.decode(buf).mouths().getFirst().swallowed());
    }

    /**
     * decision hole-mouth-opens-three-by-three-and-fits-its-room
     */
    @Test
    void footprintsRoundTripThroughTheStreamCodec() {
        List<BlockPos> threeByThree = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                threeByThree.add(new BlockPos(4 + x, 64, -2 + z));
            }
        }
        List<BlockPos> oneByTwo = List.of(new BlockPos(4, 64, -2), new BlockPos(5, 64, -2));
        PortableHolesSyncPayload sent = new PortableHolesSyncPayload(List.of(
                new PortableHolesSyncPayload.Mouth(threeByThree, Direction.UP, 960L, 1200L, List.of(), List.of()),
                new PortableHolesSyncPayload.Mouth(oneByTwo, Direction.DOWN, 960L, 1200L, List.of(), List.of())));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        PortableHolesSyncPayload.STREAM_CODEC.encode(buf, sent);
        List<PortableHolesSyncPayload.Mouth> received = PortableHolesSyncPayload.STREAM_CODEC.decode(buf).mouths();

        assertEquals(threeByThree, received.get(0).footprint());
        assertEquals(oneByTwo, received.get(1).footprint());
    }
}
