package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a Spire lifts and what it costs: each footprint cell's column of
 * ground, rise blocks deep, moves up by the rise, and the gap it leaves fills
 * with the mundane block of its depth, deepslate below Y 0 and stone above.
 * The rock goo spent is the goo value of every refill block placed. A column
 * lifts only where every block in it is one the ability may lift and every
 * cell it rises into stands open; the rest of the footprint stays put and
 * costs nothing. The client prices its preview through the same plan the
 * server lifts by.
 * decision spire-rips-walls-and-platforms
 *
 * @param footprint the footprint at the rise planned
 * @param columns   the ground cells whose columns lift
 * @param cost      the rock goo the refill costs, in mB
 */
public record SpireLift(SpireFootprint footprint, List<BlockPos> columns, int cost) {

    /** The world height under which the refill is deepslate rather than stone. */
    public static final int DEEPSLATE_BELOW_Y = 0;

    /**
     * Plans a footprint's lift at its rise.
     *
     * @param blocks    the world the ground stands in
     * @param footprint the footprint
     * @param lifts     the blocks the ability may lift
     * @param values    the goo values the refill is priced by
     * @param gooType   the goo type the cost is paid in
     * @return the plan, empty of columns where nothing can lift
     */
    public static SpireLift plan(BlockGetter blocks, SpireFootprint footprint, TagKey<Block> lifts,
                                 IGooValueLookup values, ResourceKey<GooTypeDefinition> gooType) {
        List<BlockPos> columns = new ArrayList<>();
        int cost = 0;
        for (BlockPos ground : footprint.groundCells()) {
            if (columnLifts(blocks, ground, footprint.rise(), lifts)) {
                columns.add(ground);
                cost += columnRefillCost(ground, footprint.rise(), values, gooType);
            }
        }
        return new SpireLift(footprint, List.copyOf(columns), cost);
    }

    /**
     * The highest lift the holdings pay for: the footprint at the rise asked,
     * lowered a block at a time until its refill costs no more than the
     * holdings, where some column still lifts.
     *
     * @param blocks    the world the ground stands in
     * @param footprint the footprint at the rise asked
     * @param lifts     the blocks the ability may lift
     * @param values    the goo values the refill is priced by
     * @param gooType   the goo type the cost is paid in
     * @param holdings  the mB of that type the caster holds
     * @return the plan, or empty where no rise lifts a column the holdings pay for
     */
    public static Optional<SpireLift> affordable(BlockGetter blocks, SpireFootprint footprint, TagKey<Block> lifts,
                                                 IGooValueLookup values, ResourceKey<GooTypeDefinition> gooType,
                                                 int holdings) {
        for (int rise = footprint.rise(); rise >= SpireFootprint.MIN_RISE; rise--) {
            SpireLift lift = plan(blocks, footprint.withRise(rise), lifts, values, gooType);
            if (!lift.columns().isEmpty() && lift.cost() <= holdings) {
                return Optional.of(lift);
            }
        }
        return Optional.empty();
    }

    /**
     * The mundane block that refills the ground at a height.
     *
     * @param y the height
     * @return deepslate below Y 0, stone at and above it
     */
    public static BlockState refillAt(int y) {
        return (y < DEEPSLATE_BELOW_Y ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
    }

    /**
     * The cells under a ground cell that the lift empties and the refill fills,
     * the column's own cells, rise blocks deep from the ground cell down.
     *
     * @param ground the ground cell
     * @param rise   the rise
     * @return the column's cells, top first
     */
    public static List<BlockPos> columnCells(BlockPos ground, int rise) {
        List<BlockPos> cells = new ArrayList<>(rise);
        for (int depth = 0; depth < rise; depth++) {
            cells.add(ground.below(depth));
        }
        return cells;
    }

    private static boolean columnLifts(BlockGetter blocks, BlockPos ground, int rise, TagKey<Block> lifts) {
        for (int step = 0; step < rise; step++) {
            BlockState source = blocks.getBlockState(ground.below(step));
            BlockState destination = blocks.getBlockState(ground.above(rise - step));
            if (source.isAir() || !source.is(lifts) || !destination.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    private static int columnRefillCost(BlockPos ground, int rise, IGooValueLookup values,
                                        ResourceKey<GooTypeDefinition> gooType) {
        int cost = 0;
        for (BlockPos cell : columnCells(ground, rise)) {
            cost += refillValue(refillAt(cell.getY()), values, gooType);
        }
        return cost;
    }

    private static int refillValue(BlockState refill, IGooValueLookup values, ResourceKey<GooTypeDefinition> gooType) {
        GooValue value = values.lookup(BuiltInRegistries.ITEM.getKey(refill.getBlock().asItem()));
        return value == null ? 0 : value.get(gooType);
    }
}
