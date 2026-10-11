package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.network.LurkerPulsePayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import java.util.ArrayList;
import java.util.List;

/**
 * Stands in for a mock server player's connection, claims the lurker pulse
 * channel the mock connection never negotiated, and records each pulse sent
 * to it.
 */
public final class LurkerPulseRecorder extends ServerGamePacketListenerImpl {

    private final List<LurkerPulsePayload> pulses = new ArrayList<>();

    private LurkerPulseRecorder(ServerPlayer player) {
        super(player.level().getServer(), player.connection.getConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
    }

    /**
     * Replaces the player's connection with a recorder.
     *
     * @param player the mock server player
     * @return the recorder, now the player's connection
     */
    public static LurkerPulseRecorder attachTo(ServerPlayer player) {
        return new LurkerPulseRecorder(player);
    }

    /**
     * Every lurker pulse sent to the player since the recorder attached, in send order.
     *
     * @return the pulses
     */
    public List<LurkerPulsePayload> pulses() {
        return List.copyOf(pulses);
    }

    @Override
    public boolean hasChannel(CustomPacketPayload payload) {
        return payload instanceof LurkerPulsePayload;
    }

    @Override
    public void send(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof LurkerPulsePayload pulse) {
            pulses.add(pulse);
        }
    }
}
