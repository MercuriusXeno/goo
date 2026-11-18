package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What a Build grows and what it costs: each footprint column reaches down
 * as far as it rises and lifts the blocks it reached by the rise on stone
 * grown in the cells they leave (BuildColumn). A column grows only where
 * every cell above the ground the rise fills can be replaced; the rest of
 * the footprint grows nothing and costs nothing.
 * The goo spent is the ability's exact per-block price for every stone block
 * grown, charged through the caster's ledger. The client prices its preview
 * through the same plan the server grows by.
 * decision spire-grows-stone-out-of-the-top
 * decision build-reaches-down-as-far-as-it-rises-and-lifts-the-ground-on-stone
 * decision spire-cost-field-prices-each-cubic-meter-of-stone-at-mundane-block
 * decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer
 *
 * @param footprint the footprint at the rise planned
 * @param columns   the columns that grow
 * @param exactCost the exact goo the stone grown costs
 */
public record BuildGrowth(BuildFootprint footprint, List<BuildColumn> columns, GooFraction exactCost) {

    /**
     * Plans a footprint's growth at its rise.
     *
     * @param blocks        the world the footprint stands in
     * @param footprint     the footprint
     * @param pricePerBlock the exact goo each stone block grown costs, the ability's cost resolved in its type
     * @return the plan, empty of columns where nothing can grow
     */
    public static BuildGrowth plan(BlockGetter blocks, BuildFootprint footprint, GooFraction pricePerBlock) {
        List<BuildColumn> columns = new ArrayList<>();
        for (BlockPos ground : footprint.groundCells()) {
            if (columnOpen(blocks, ground, footprint.rise())) {
                columns.add(BuildColumn.reaching(ground, footprint.rise(), cell -> carriable(blocks, cell)));
            }
        }
        GooFraction cost = pricePerBlock.times((long) columns.size() * footprint.rise());
        return new BuildGrowth(footprint, List.copyOf(columns), cost);
    }

    /**
     * The highest growth the holdings pay for: the footprint at the rise
     * asked, lowered a block at a time until its stone costs no more than the
     * holdings, where some column still grows.
     *
     * @param blocks        the world the footprint stands in
     * @param footprint     the footprint at the rise asked
     * @param pricePerBlock the exact goo each stone block grown costs
     * @param gooType       the goo type the cost is paid in
     * @param remainders    the caster's remainders, which pay first
     * @param holdings      the mB of that type the caster holds
     * @return the plan, or empty where no rise grows a column the holdings pay for
     */
    public static Optional<BuildGrowth> affordable(BlockGetter blocks, BuildFootprint footprint,
                                                   GooFraction pricePerBlock, ResourceKey<GooTypeDefinition> gooType,
                                                   GooRemainders remainders, int holdings) {
        for (int rise = footprint.rise(); rise >= BuildBounds.MIN_RISE; rise--) {
            BuildGrowth growth = plan(blocks, footprint.withRise(rise), pricePerBlock);
            // decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer
            boolean paidFor = new RemainderLedger(remainders).debitExact(gooType, growth.exactCost(), holdings)
                    .isPresent();
            if (!growth.columns().isEmpty() && paidFor) {
                return Optional.of(growth);
            }
        }
        return Optional.empty();
    }

    /**
     * The most whole mB the stone may charge, what a caster's holdings must cover
     * whatever remainder their ledger holds.
     *
     * @return the exact cost rounded up, in mB
     */
    public int cost() {
        return exactCost.ceiling();
    }

    /**
     * Debits the stone's exact cost through the caster's ledger, the remainder
     * paying first and the overpayment banked.
     *
     * @param ledger   the caster's remainder ledger
     * @param gooType  the goo type the cost is paid in
     * @param holdings the whole mB of that type the caster holds
     * @return the whole mB to take from the holdings, or empty where they cannot cover it
     */
    public OptionalInt chargeThrough(IRemainderLedger ledger, ResourceKey<GooTypeDefinition> gooType, int holdings) {
        return ledger.debitExact(gooType, exactCost, holdings);
    }

    /**
     * Whether the rising hunk can carry the block in a cell: one a hand can
     * break and holding no block entity, inside the world; bedrock stops a
     * column's reach, and so does a chest, whose contents the hunk cannot hold.
     * decision build-reaches-down-as-far-as-it-rises-and-lifts-the-ground-on-stone
     *
     * @param blocks the world
     * @param cell   the cell
     * @return true where the hunk can carry its block
     */
    static boolean carriable(BlockGetter blocks, BlockPos cell) {
        BlockState state = blocks.getBlockState(cell);
        return cell.getY() >= blocks.getMinY() && !state.hasBlockEntity() && state.getDestroySpeed(blocks, cell) >= 0;
    }

    private static boolean columnOpen(BlockGetter blocks, BlockPos ground, int rise) {
        for (int height = 1; height <= rise; height++) {
            if (!blocks.getBlockState(ground.above(height)).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }
}
