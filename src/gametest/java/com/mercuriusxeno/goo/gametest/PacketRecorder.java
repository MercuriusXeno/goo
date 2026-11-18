package com.mercuriusxeno.goo.gametest;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import java.util.ArrayList;
import java.util.List;

/**
 * Stands in for a mock server player's connection and records every packet the
 * server sends that player, so a test reads the particles, sounds and messages a
 * click delivers.
 */
public final class PacketRecorder extends ServerGamePacketListenerImpl {

    private final List<Packet<?>> sent = new ArrayList<>();
    private final @Nullable Class<? extends CustomPacketPayload> claimed;

    private PacketRecorder(ServerPlayer player, @Nullable Class<? extends CustomPacketPayload> claimed) {
        super(player.level().getServer(), player.connection.getConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        this.claimed = claimed;
    }

    /**
     * Replaces the player's connection with a recorder.
     *
     * @param player the mock server player
     * @return the recorder, now the player's connection
     */
    public static PacketRecorder attachTo(ServerPlayer player) {
        return new PacketRecorder(player, null);
    }

    /**
     * Replaces the player's connection with a recorder that also claims one
     * payload's channel, which a mock connection never negotiates, so that
     * payload reaches it beside every vanilla packet.
     *
     * @param player  the mock server player
     * @param claimed the payload class whose channel the recorder claims
     * @return the recorder, now the player's connection
     */
    public static PacketRecorder attachClaiming(ServerPlayer player, Class<? extends CustomPacketPayload> claimed) {
        return new PacketRecorder(player, claimed);
    }

    /**
     * The payloads of one kind sent to the player since the recorder attached, in send order.
     *
     * @param kind the payload class
     * @param <T>  the payload type
     * @return the payloads
     */
    public <T extends CustomPacketPayload> List<T> payloadsOf(Class<T> kind) {
        return sentOf(ClientboundCustomPayloadPacket.class).stream().map(ClientboundCustomPayloadPacket::payload)
                .filter(kind::isInstance).map(kind::cast).toList();
    }

    @Override
    public boolean hasChannel(CustomPacketPayload payload) {
        return claimed != null && claimed.isInstance(payload) || super.hasChannel(payload);
    }

    /**
     * The packets of one kind sent to the player since the recorder attached, in send order.
     *
     * @param kind the packet class
     * @param <P>  the packet type
     * @return the packets of that kind
     */
    public <P extends Packet<?>> List<P> sentOf(Class<P> kind) {
        return sent.stream().filter(kind::isInstance).map(kind::cast).toList();
    }

    @Override
    public void send(Packet<?> packet) {
        sent.add(packet);
    }
}
