package com.mercuriusxeno.goo.gametest;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
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

    private PacketRecorder(ServerPlayer player) {
        super(player.level().getServer(), player.connection.getConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
    }

    /**
     * Replaces the player's connection with a recorder.
     *
     * @param player the mock server player
     * @return the recorder, now the player's connection
     */
    public static PacketRecorder attachTo(ServerPlayer player) {
        return new PacketRecorder(player);
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
