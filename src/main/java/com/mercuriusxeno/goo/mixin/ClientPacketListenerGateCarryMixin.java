package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.ClientGateCarry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops the server's word placing a body back at the landing a gate carry
 * just set it at, read on the client thread: it would pull the body back
 * there and stall it at the partner's face while it moves on.
 * aim-and-shots-carry-through-linked-gates
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerGateCarryMixin {

    private static final String ON_THE_CLIENT_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;"
            + "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;"
            + "Lnet/minecraft/network/PacketProcessor;)V";

    @Shadow
    private ClientLevel level;

    /**
     * Drops a position sync placing a lately carried body back at its landing.
     *
     * @param packet the sync
     * @param ci     the callback
     */
    @Inject(method = "handleEntityPositionSync", at = @At(value = "INVOKE", target = ON_THE_CLIENT_THREAD,
            shift = At.Shift.AFTER), cancellable = true)
    private void goo$dropStaleCarrySync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        if (level != null && ClientGateCarry.staleSync(packet.id(), packet.values().position(), level.getGameTime())) {
            ci.cancel();
        }
    }

    /**
     * Drops a teleport placing a lately carried body back at its landing.
     *
     * @param packet the teleport
     * @param ci     the callback
     */
    @Inject(method = "handleTeleportEntity", at = @At(value = "INVOKE", target = ON_THE_CLIENT_THREAD,
            shift = At.Shift.AFTER), cancellable = true)
    private void goo$dropStaleCarryTeleport(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        if (level != null && packet.relatives().isEmpty()
                && ClientGateCarry.staleSync(packet.id(), packet.change().position(), level.getGameTime())) {
            ci.cancel();
        }
    }
}
