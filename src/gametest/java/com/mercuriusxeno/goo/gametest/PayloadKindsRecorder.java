package com.mercuriusxeno.goo.gametest;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Stands in for a mock server player's connection, claims the channels of
 * several payload kinds the mock connection never negotiated, and records
 * each payload of those kinds sent to it, so a test reads which of them an
 * act sent and which it did not.
 */
public final class PayloadKindsRecorder extends ServerGamePacketListenerImpl {

    private final Set<Class<? extends CustomPacketPayload>> kinds;
    private final List<CustomPacketPayload> received = new ArrayList<>();

    private PayloadKindsRecorder(ServerPlayer player, Set<Class<? extends CustomPacketPayload>> kinds) {
        super(player.level().getServer(), player.connection.getConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        this.kinds = kinds;
    }

    /**
     * Replaces the player's connection with a recorder of the payload kinds given.
     *
     * @param player the mock server player
     * @param kinds  the payload classes recorded
     * @return the recorder, now the player's connection
     */
    public static PayloadKindsRecorder attachTo(ServerPlayer player, Set<Class<? extends CustomPacketPayload>> kinds) {
        return new PayloadKindsRecorder(player, kinds);
    }

    /**
     * The payloads of one recorded kind sent to the player since the recorder attached, in send order.
     *
     * @param kind the payload class
     * @param <T>  the payload type
     * @return the payloads of that kind
     */
    public <T extends CustomPacketPayload> List<T> receivedOf(Class<T> kind) {
        return received.stream().filter(kind::isInstance).map(kind::cast).toList();
    }

    @Override
    public boolean hasChannel(CustomPacketPayload payload) {
        return kinds.stream().anyMatch(kind -> kind.isInstance(payload));
    }

    @Override
    public void send(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom && hasChannel(custom.payload())) {
            received.add(custom.payload());
        }
    }
}
