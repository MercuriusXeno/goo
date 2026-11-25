package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import java.util.Optional;

/**
 * Client-to-server payload: a charged ability let go, carrying how many
 * ticks the glove's use was held, which the server reads as the share of a
 * full charge (decision nova-ring-grows-with-the-hold), and, for a
 * spontaneous cast, the target the crosshair aimed as it let go
 * (decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob)
 * with where the caster's eye stood as the charge rooted, which the range
 * gate measures from (decision what-starts-in-range-resolves-out-of-range).
 *
 * @param gooTypeId   the goo type string identifier
 * @param abilityId   the selected ability id string
 * @param heldTicks   the ticks the use key was held before release
 * @param aimed       the target a spontaneous cast lands at, empty for a charge aiming none
 * @param eyeAsRooted where the caster's eye stood as the charge rooted, empty for a charge that rooted nowhere
 */
public record GooChargePayload(String gooTypeId, String abilityId, int heldTicks,
                               Optional<GooThrowPayload> aimed, Optional<Vec3> eyeAsRooted)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GooChargePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "goo_charge"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GooChargePayload> STREAM_CODEC =
            StreamCodec.of(GooChargePayload::encode, GooChargePayload::decode);

    /**
     * A charge aiming a target from a charge that rooted nowhere.
     *
     * @param gooTypeId the goo type string identifier
     * @param abilityId the selected ability id string
     * @param heldTicks the ticks the use key was held before release
     * @param aimed     the target a spontaneous cast lands at, empty for a charge aiming none
     */
    public GooChargePayload(String gooTypeId, String abilityId, int heldTicks, Optional<GooThrowPayload> aimed) {
        this(gooTypeId, abilityId, heldTicks, aimed, Optional.empty());
    }

    /**
     * A charge aiming no target: a self ability's or a slinging throw's.
     *
     * @param gooTypeId the goo type string identifier
     * @param abilityId the selected ability id string
     * @param heldTicks the ticks the use key was held before release
     */
    public GooChargePayload(String gooTypeId, String abilityId, int heldTicks) {
        this(gooTypeId, abilityId, heldTicks, Optional.empty());
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, GooChargePayload payload) {
        buf.writeUtf(payload.gooTypeId);
        buf.writeUtf(payload.abilityId);
        buf.writeVarInt(payload.heldTicks);
        buf.writeBoolean(payload.aimed.isPresent());
        payload.aimed.ifPresent(target -> GooThrowPayload.STREAM_CODEC.encode(buf, target));
        buf.writeBoolean(payload.eyeAsRooted.isPresent());
        payload.eyeAsRooted.ifPresent(eye -> Vec3.STREAM_CODEC.encode(buf, eye));
    }

    private static GooChargePayload decode(FriendlyByteBuf buf) {
        String gooTypeId = buf.readUtf();
        String abilityId = buf.readUtf();
        int heldTicks = buf.readVarInt();
        Optional<GooThrowPayload> aimed = buf.readBoolean()
                ? Optional.of(GooThrowPayload.STREAM_CODEC.decode(buf)) : Optional.empty();
        Optional<Vec3> eyeAsRooted = buf.readBoolean() ? Optional.of(Vec3.STREAM_CODEC.decode(buf)) : Optional.empty();
        return new GooChargePayload(gooTypeId, abilityId, heldTicks, aimed, eyeAsRooted);
    }
}
