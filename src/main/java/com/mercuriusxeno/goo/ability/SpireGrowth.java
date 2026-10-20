package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a Spire grows and what it costs: stone fills the cells above each
 * footprint cell, from one above the ground through the rise, and the ground
 * stays as it stood. A column grows only where every cell it would fill can
 * be replaced; the rest of the footprint grows nothing and costs nothing.
 * The rock goo spent is stone's goo value for every stone block grown. The
 * client prices its preview through the same plan the server grows by.
 * decision spire-grows-stone-out-of-the-top
 *
 * @param footprint the footprint at the rise planned
 * @param columns   the ground cells whose columns grow
 * @param cost      the goo the stone grown costs, in mB
 */
public record SpireGrowth(SpireFootprint footprint, List<BlockPos> columns, int cost) {

    /** The item stone's goo value is looked up by, the block every Spire grows. */
    private static final Identifier STONE_ITEM = Identifier.withDefaultNamespace("stone");

    /**
     * Plans a footprint's growth at its rise.
     *
     * @param blocks    the world the footprint stands in
     * @param footprint the footprint
     * @param values    the goo values the stone is priced by
     * @param gooType   the goo type the cost is paid in
     * @return the plan, empty of columns where nothing can grow
     */
    public static SpireGrowth plan(BlockGetter blocks, SpireFootprint footprint, IGooValueLookup values,
                                   ResourceKey<GooTypeDefinition> gooType) {
        List<BlockPos> columns = new ArrayList<>();
        for (BlockPos ground : footprint.groundCells()) {
            if (columnOpen(blocks, ground, footprint.rise())) {
                columns.add(ground);
            }
        }
        int cost = stonePrice(values, gooType) * columns.size() * footprint.rise();
        return new SpireGrowth(footprint, List.copyOf(columns), cost);
    }

    /**
     * The highest growth the holdings pay for: the footprint at the rise
     * asked, lowered a block at a time until its stone costs no more than the
     * holdings, where some column still grows.
     *
     * @param blocks    the world the footprint stands in
     * @param footprint the footprint at the rise asked
     * @param values    the goo values the stone is priced by
     * @param gooType   the goo type the cost is paid in
     * @param holdings  the mB of that type the caster holds
     * @return the plan, or empty where no rise grows a column the holdings pay for
     */
    public static Optional<SpireGrowth> affordable(BlockGetter blocks, SpireFootprint footprint,
                                                   IGooValueLookup values, ResourceKey<GooTypeDefinition> gooType,
                                                   int holdings) {
        for (int rise = footprint.rise(); rise >= SpireFootprint.MIN_RISE; rise--) {
            SpireGrowth growth = plan(blocks, footprint.withRise(rise), values, gooType);
            if (!growth.columns().isEmpty() && growth.cost() <= holdings) {
                return Optional.of(growth);
            }
        }
        return Optional.empty();
    }

    private static boolean columnOpen(BlockGetter blocks, BlockPos ground, int rise) {
        for (int height = 1; height <= rise; height++) {
            if (!blocks.getBlockState(ground.above(height)).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    private static int stonePrice(IGooValueLookup values, ResourceKey<GooTypeDefinition> gooType) {
        GooValue value = values.lookup(STONE_ITEM);
        return value == null ? 0 : value.get(gooType);
    }
}
