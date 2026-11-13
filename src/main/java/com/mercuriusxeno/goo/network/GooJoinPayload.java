package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.item.GooAbsorb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Tells tracking clients that a goo item absorbed its neighbors, so each
 * absorbed cuboid draws flowing into the anchor while the anchor eases to the
 * summed volume's size.
 * decision combining-flecks-join-into-one-cuboid
 *
 * @param anchorId     the absorbing goo item entity's id
 * @param anchorPlace  where the anchor stood when it absorbed
 * @param joinedVolume the anchor's volume after the absorb, in mB
 * @param absorbed     each absorbed goo item's place and volume
 */
public record GooJoinPayload(int anchorId, Vec3 anchorPlace, int joinedVolume, List<Absorbed> absorbed)
        implements CustomPacketPayload {

    /** The payload's type. */
    public static final Type<GooJoinPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "goo_join"));

    /** The payload's wire codec. */
    public static final StreamCodec<FriendlyByteBuf, GooJoinPayload> STREAM_CODEC =
            StreamCodec.of(GooJoinPayload::encode, GooJoinPayload::decode);

    /**
     * An absorbed goo item, where it stood and the goo it held.
     *
     * @param place  where it stood when absorbed
     * @param volume the goo it held, in mB
     */
    public record Absorbed(Vec3 place, int volume) {
    }

    /**
     * An absorb candidate beside where it stands.
     *
     * @param candidate the candidate the absorb rule read
     * @param place     where it stands
     */
    public record Placed(GooAbsorb.Candidate candidate, Vec3 place) {
    }

    /**
     * Builds the join an absorb makes: each candidate the result discards,
     * at its place with its volume.
     *
     * @param anchorId    the absorbing goo item entity's id
     * @param anchorPlace where the anchor stands
     * @param result      the absorb's result
     * @param candidates  the candidates the result was computed from, each beside its place
     * @return the join message
     */
    public static GooJoinPayload of(int anchorId, Vec3 anchorPlace, GooAbsorb.Result result, List<Placed> candidates) {
        List<Absorbed> absorbed = new ArrayList<>();
        for (Placed placed : candidates) {
            if (result.discardIds().contains(placed.candidate().id())) {
                absorbed.add(new Absorbed(placed.place(), placed.candidate().volume()));
            }
        }
        return new GooJoinPayload(anchorId, anchorPlace, result.volume(), List.copyOf(absorbed));
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, GooJoinPayload payload) {
        buf.writeVarInt(payload.anchorId);
        Vec3.STREAM_CODEC.encode(buf, payload.anchorPlace);
        buf.writeVarInt(payload.joinedVolume);
        buf.writeVarInt(payload.absorbed.size());
        for (Absorbed each : payload.absorbed) {
            Vec3.STREAM_CODEC.encode(buf, each.place());
            buf.writeVarInt(each.volume());
        }
    }

    private static GooJoinPayload decode(FriendlyByteBuf buf) {
        int anchorId = buf.readVarInt();
        Vec3 anchorPlace = Vec3.STREAM_CODEC.decode(buf);
        int joinedVolume = buf.readVarInt();
        int count = buf.readVarInt();
        List<Absorbed> absorbed = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            absorbed.add(new Absorbed(Vec3.STREAM_CODEC.decode(buf), buf.readVarInt()));
        }
        return new GooJoinPayload(anchorId, anchorPlace, joinedVolume, List.copyOf(absorbed));
    }
}
