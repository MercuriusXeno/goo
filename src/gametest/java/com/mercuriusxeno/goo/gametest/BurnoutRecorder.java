package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import java.util.ArrayList;
import java.util.List;

/**
 * Stands in for a mock server player's connection, claims the burnout
 * channel the mock connection never negotiated, and records each burnout
 * sent to it.
 */
public final class BurnoutRecorder extends ServerGamePacketListenerImpl {

    private final List<ChainBurnoutPayload> burnouts = new ArrayList<>();

    private BurnoutRecorder(ServerPlayer player) {
        super(player.level().getServer(), player.connection.getConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
    }

    /**
     * Replaces the player's connection with a recorder.
     *
     * @param player the mock server player
     * @return the recorder, now the player's connection
     */
    public static BurnoutRecorder attachTo(ServerPlayer player) {
        return new BurnoutRecorder(player);
    }

    /**
     * Every burnout sent to the player since the recorder attached, in send order.
     *
     * @return the burnouts
     */
    public List<ChainBurnoutPayload> burnouts() {
        return List.copyOf(burnouts);
    }

    @Override
    public boolean hasChannel(CustomPacketPayload payload) {
        return payload instanceof ChainBurnoutPayload;
    }

    @Override
    public void send(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof ChainBurnoutPayload burnout) {
            burnouts.add(burnout);
        }
    }
}
