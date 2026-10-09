package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.Optional;

/**
 * What a blink adds to its ability's flat cost for the trip it makes: an
 * amount per block travelled and a surcharge when the trip passed through a
 * solid block. Every ability that names neither pays its flat cost alone.
 * Decision blink-lands-safely-costed-by-distance.
 *
 * @param perBlock the mB each block travelled adds
 * @param wallCost the mB a trip through a solid block adds
 */
public record DistancePrice(int perBlock, int wallCost) {

    /** The price of an ability whose cost reads no trip. */
    public static final DistancePrice NONE = new DistancePrice(0, 0);

    private static final String FIELD_COST_PER_BLOCK = "cost_per_block";
    private static final String FIELD_WALL_COST = "wall_cost";

    /** Reads the two fields from the ability's own JSON object, each zero when absent. */
    public static final MapCodec<DistancePrice> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf(FIELD_COST_PER_BLOCK, 0)
                    .forGetter(DistancePrice::perBlock),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf(FIELD_WALL_COST, 0)
                    .forGetter(DistancePrice::wallCost)
    ).apply(inst, DistancePrice::new));

    /** Carries the price to the client, which shows the live cost. */
    public static final StreamCodec<ByteBuf, DistancePrice> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DistancePrice::perBlock,
            ByteBufCodecs.VAR_INT, DistancePrice::wallCost,
            DistancePrice::new);

    /**
     * The cost of a trip: the flat cost, plus the per-block amount for every
     * block travelled, rounded up, plus the surcharge when it passed through
     * a wall.
     *
     * @param base    the ability's flat cost
     * @param landing the trip
     * @return the cost in mB
     */
    public int price(int base, BlinkLanding landing) {
        int travel = (int) Math.ceil(perBlock * landing.distance());
        return base + travel + (landing.throughWall() ? wallCost : 0);
    }

    /**
     * The cost of a trip where one resolves, the flat cost where none does.
     *
     * @param base    the ability's flat cost
     * @param landing the trip, empty when the ability makes none
     * @return the cost in mB
     */
    public int priceOf(int base, Optional<BlinkLanding> landing) {
        return landing.map(trip -> price(base, trip)).orElse(base);
    }
}
