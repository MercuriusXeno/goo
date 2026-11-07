package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.PrefabValues;
import com.mercuriusxeno.goo.type.GooTypes;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The value sync payload carries fractional values whole through its stream codec
 * (decision values-are-fractions-melt-floors-fabricate-ceils).
 */
class GooValueSyncPayloadTest {

    @Test
    void aFractionalValueRoundTripsThroughTheStreamCodec() {
        GooValueSyncPayload sent = new GooValueSyncPayload(Map.of(
                Identifier.withDefaultNamespace("white_carpet"),
                fraction(GooTypes.LEAF, 64, 3, GooTypes.JELLY, 2, 1)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        GooValueSyncPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(sent, GooValueSyncPayload.STREAM_CODEC.decode(buf));
    }

    /** The payload carries each prefab's value, so the wheel prices a cost naming a prefab (decision ability-cost-builds-as-a-one-type-by-type-value). */
    @Test
    void prefabValuesRoundTripThroughTheStreamCodec() {
        PrefabValues prefabs = new PrefabValues(Map.of("base", GooFraction.of(7, 2)),
                Map.of("stone", fraction(GooTypes.ROCK, 250, 1, GooTypes.LEAF, 1, 3)));
        GooValueSyncPayload sent = new GooValueSyncPayload(Map.of(), prefabs);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        GooValueSyncPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(prefabs, GooValueSyncPayload.STREAM_CODEC.decode(buf).prefabs());
    }
}
