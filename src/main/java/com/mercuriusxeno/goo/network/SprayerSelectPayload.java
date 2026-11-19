package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.item.SprayerFlow;
import com.mercuriusxeno.goo.item.SprayerItem;
import com.mercuriusxeno.goo.item.SprayerSetting;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import java.util.Optional;

/**
 * Client-to-server payload: the goo type and flow picked on the sprayer's wheel, which the held sprayer keeps, as
 * GloveSelectPayload carries the glove's pick.
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 *
 * @param gooTypeId the picked goo type's id
 * @param flow      the picked flow
 */
public record SprayerSelectPayload(String gooTypeId, SprayerFlow flow) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<SprayerSelectPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "sprayer_select"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, SprayerSelectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SprayerSelectPayload::gooTypeId,
            SprayerFlow.STREAM_CODEC.cast(), SprayerSelectPayload::flow,
            SprayerSelectPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Keeps the pick on the sprayer the player holds, on the server thread.
     *
     * @param payload the pick
     * @param context the network context
     */
    public static void handle(SprayerSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                keepOnTheHeldSprayer(player, payload);
            }
        });
    }

    /**
     * Keeps a pick on the sprayer the player holds; a pick naming no known goo type, or a player holding no
     * sprayer, changes nothing. The sprayer reads no recipe knowledge: a residual is its goo type's resting
     * behavior.
     *
     * @param player  the picking player
     * @param payload the pick
     */
    public static void keepOnTheHeldSprayer(ServerPlayer player, SprayerSelectPayload payload) {
        ItemStack sprayer = SprayerItem.heldSprayer(player);
        ResourceKey<GooTypeDefinition> type = GooTypes.known(payload.gooTypeId());
        if (sprayer.isEmpty() || type == null) {
            return;
        }
        SprayerItem.keepSetting(sprayer, new SprayerSetting(Optional.of(type), payload.flow()));
    }
}
