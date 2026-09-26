package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.network.TunerFeedbackPayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import java.util.ArrayList;
import java.util.List;

/**
 * Stands in for a mock server player's connection and records the tuner feedback
 * sent to it; the mock connection never negotiated goo's payloads, so NeoForge
 * refuses to send them through the real listener.
 */
public final class TunerFeedbackRecorder extends ServerGamePacketListenerImpl {

    private final List<String> lines = new ArrayList<>();

    private TunerFeedbackRecorder(ServerPlayer player) {
        super(player.level().getServer(), player.connection.getConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
    }

    /**
     * Replaces the player's connection with a recorder.
     *
     * @param player the mock server player
     * @return the recorder, now the player's connection
     */
    public static TunerFeedbackRecorder attachTo(ServerPlayer player) {
        return new TunerFeedbackRecorder(player);
    }

    /**
     * Every tuner feedback line sent to the player since the recorder attached, in send order.
     *
     * @return the feedback lines
     */
    public List<String> lines() {
        return List.copyOf(lines);
    }

    @Override
    public void send(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof TunerFeedbackPayload feedback) {
            lines.addAll(feedback.lines());
        }
    }
}
