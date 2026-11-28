package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.program.ChannelAim;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the payload carrying the Mortar a Level hold carries to the
 * watching clients: it crosses the network with its disc and its nine piles whole.
 * carried-mortar-stops-being-a-block-on-the-level-plane
 */
class MortarCarryPayloadTest {

    private static final int HOLDER = 42;
    private static final ChannelAim.FacePlane PLANE = new ChannelAim.FacePlane(new BlockPos(3, 64, -7), Direction.UP);
    private static final BlockPos MIDDLE = new BlockPos(4, 64, -6);
    private static final List<Integer> PILES = List.of(12, 3, 0, 15, 1, 0, 0, 7, 2);

    @Test
    void theDiscAndItsNinePilesCrossTheNetworkWhole() {
        MortarCarryPayload sent = new MortarCarryPayload(HOLDER, PLANE, MIDDLE, PILES);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        MortarCarryPayload.STREAM_CODEC.encode(buf, sent);
        assertEquals(sent, MortarCarryPayload.STREAM_CODEC.decode(buf));
    }
}
