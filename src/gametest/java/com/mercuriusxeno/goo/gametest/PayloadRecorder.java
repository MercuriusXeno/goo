package com.mercuriusxeno.goo.gametest;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import java.util.ArrayList;
import java.util.List;

/**
 * Stands in for a mock server player's connection, claims one payload's
 * channel the mock connection never negotiated, and records each payload of
 * that kind sent to it.
 *
 * @param <T> the payload recorded
 */
public final class PayloadRecorder<T extends CustomPacketPayload> extends ServerGamePacketListenerImpl {

    private final Class<T> kind;
    private final List<T> received = new ArrayList<>();

    private PayloadRecorder(ServerPlayer player, Class<T> kind) {
        super(player.level().getServer(), player.connection.getConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        this.kind = kind;
    }

    /**
     * Replaces the player's connection with a recorder of one payload kind.
     *
     * @param player the mock server player
     * @param kind   the payload class recorded
     * @param <T>    the payload recorded
     * @return the recorder, now the player's connection
     */
    public static <T extends CustomPacketPayload> PayloadRecorder<T> attachTo(ServerPlayer player, Class<T> kind) {
        return new PayloadRecorder<>(player, kind);
    }

    /**
     * Every payload of the recorded kind sent to the player since the recorder attached, in send order.
     *
     * @return the payloads
     */
    public List<T> received() {
        return List.copyOf(received);
    }

    @Override
    public boolean hasChannel(CustomPacketPayload payload) {
        return kind.isInstance(payload);
    }

    @Override
    public void send(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom && kind.isInstance(custom.payload())) {
            received.add(kind.cast(custom.payload()));
        }
    }
}
