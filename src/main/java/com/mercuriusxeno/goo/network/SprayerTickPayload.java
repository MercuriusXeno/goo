package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: one tick of a sprayer's hold, sent every tick its use stays down, naming where the
 * drips leave; the server reads the goo and the residual from the sprayer's own canister.
 * decision spray-item-sprays-residuals-beside-the-glove
 *
 * @param origin the sprayer's nozzle end in the caster's view
 */
public record SprayerTickPayload(Vec3 origin) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<SprayerTickPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "sprayer_tick"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, SprayerTickPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeDouble(payload.origin.x);
                buf.writeDouble(payload.origin.y);
                buf.writeDouble(payload.origin.z);
            }, buf -> new SprayerTickPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Runs the sprayer's tick on the server thread.
     *
     * @param payload the sprayer's tick
     * @param context the network context
     */
    public static void handle(SprayerTickPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                GooStreamHandler.sprayerTick(player, payload.origin());
            }
        });
    }
}
