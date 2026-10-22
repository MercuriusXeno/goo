package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A goo store's remainders: each goo type's part under 1 mB, as a reduced fraction.
 * The whole mB of a store stays an int in its tanks; only this part is a fraction.
 * The constructor normalizes each remainder to its part at or above 0 and under 1,
 * dropping any whole part and any zero, so 5/3 stands as 2/3.
 * decision goo-is-an-int-and-only-the-remainder-is-a-fraction
 *
 * @param parts the remainder of each goo type, each at or above 0 and under 1
 */
public record GooRemainders(Map<ResourceKey<GooTypeDefinition>, GooFraction> parts) {

    public static final GooRemainders EMPTY = new GooRemainders(Map.of());

    /** Persistent codec: goo type ids to fractions. */
    public static final Codec<GooRemainders> CODEC = Codec.unboundedMap(GooTypes.ID_CODEC, GooFraction.CODEC)
            .xmap(GooRemainders::new, GooRemainders::parts);

    /** Network codec: each entry as its type key then its fraction. */
    public static final StreamCodec<ByteBuf, GooRemainders> STREAM_CODEC = ByteBufCodecs
            .<ByteBuf, ResourceKey<GooTypeDefinition>, GooFraction, Map<ResourceKey<GooTypeDefinition>, GooFraction>>map(
                    HashMap::new, GooTypes.KEY_STREAM_CODEC, GooFraction.STREAM_CODEC)
            .map(GooRemainders::new, GooRemainders::parts);

    /**
     * Normalizes every remainder to its part under 1 and drops the zeros.
     *
     * @param parts the raw remainders
     */
    public GooRemainders {
        Map<ResourceKey<GooTypeDefinition>, GooFraction> kept = new HashMap<>();
        parts.forEach((type, part) -> {
            GooFraction underOne = part.minus(GooFraction.of(part.floor()));
            if (!underOne.isZero()) {
                kept.put(type, underOne);
            }
        });
        parts = kept.isEmpty() ? Map.of() : Collections.unmodifiableMap(kept);
    }

    /**
     * The remainder of one goo type.
     *
     * @param type the goo type
     * @return its remainder, zero where none stands
     */
    public GooFraction of(ResourceKey<GooTypeDefinition> type) {
        return parts.getOrDefault(type, GooFraction.ZERO);
    }

    /**
     * The same remainders with one type's replaced, normalized under 1.
     *
     * @param type      the goo type
     * @param remainder its new remainder
     * @return the new remainders
     */
    public GooRemainders with(ResourceKey<GooTypeDefinition> type, GooFraction remainder) {
        Map<ResourceKey<GooTypeDefinition>, GooFraction> next = new HashMap<>(parts);
        next.put(type, remainder);
        return new GooRemainders(next);
    }

    /**
     * True where no type holds a remainder.
     *
     * @return true when empty
     */
    public boolean isEmpty() {
        return parts.isEmpty();
    }
}
