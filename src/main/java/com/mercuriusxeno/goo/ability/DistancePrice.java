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
 * solid block; a trip that snapped to an oculus costs only a share of that.
 * Every ability that names none pays its flat cost alone.
 * Decisions blink-lands-safely-costed-by-distance and oculus-prism-becomes-a-hovering-eye.
 *
 * @param perBlock    the mB each block travelled adds
 * @param wallCost    the mB a trip through a solid block adds
 * @param nodePercent the percent of the price a trip to an oculus costs
 */
public record DistancePrice(int perBlock, int wallCost, int nodePercent) {

    /** A whole price, which a trip to an oculus pays where the JSON names no share. */
    public static final int WHOLE_PERCENT = 100;
    /** The price of an ability whose cost reads no trip. */
    public static final DistancePrice NONE = new DistancePrice(0, 0);
    private static final double PERCENT = 100.0;
    private static final String FIELD_NODE_PERCENT = "node_percent";

    /**
     * A price with no oculus share: a trip to an oculus pays it whole.
     *
     * @param perBlock the mB each block travelled adds
     * @param wallCost the mB a trip through a solid block adds
     */
    public DistancePrice(int perBlock, int wallCost) {
        this(perBlock, wallCost, WHOLE_PERCENT);
    }

    private static final String FIELD_COST_PER_BLOCK = "cost_per_block";
    private static final String FIELD_WALL_COST = "wall_cost";

    /** Reads the two fields from the ability's own JSON object, each zero when absent. */
    public static final MapCodec<DistancePrice> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf(FIELD_COST_PER_BLOCK, 0)
                    .forGetter(DistancePrice::perBlock),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf(FIELD_WALL_COST, 0)
                    .forGetter(DistancePrice::wallCost),
            Codec.intRange(0, WHOLE_PERCENT).optionalFieldOf(FIELD_NODE_PERCENT, WHOLE_PERCENT)
                    .forGetter(DistancePrice::nodePercent)
    ).apply(inst, DistancePrice::new));

    /** Carries the price to the client, which shows the live cost. */
    public static final StreamCodec<ByteBuf, DistancePrice> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DistancePrice::perBlock,
            ByteBufCodecs.VAR_INT, DistancePrice::wallCost,
            ByteBufCodecs.VAR_INT, DistancePrice::nodePercent,
            DistancePrice::new);

    /**
     * The cost of a trip: the flat cost, plus the per-block amount for every
     * block travelled, rounded up, plus the surcharge when it passed through
     * a wall; a trip that snapped to an oculus pays the oculus's share of
     * that, rounded up.
     *
     * @param base    the ability's flat cost
     * @param landing the trip
     * @return the cost in mB
     */
    public int price(int base, BlinkLanding landing) {
        int travel = (int) Math.ceil(perBlock * landing.distance());
        int whole = base + travel + (landing.throughWall() ? wallCost : 0);
        return landing.node().isPresent() ? (int) Math.ceil(whole * nodePercent / PERCENT) : whole;
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
