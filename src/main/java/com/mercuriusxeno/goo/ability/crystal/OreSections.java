package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.ability.AbilityMath;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One walk's reads of an ore source: a block already visited, or in a
 * section that cannot hold ore, answers no ore without a read, and each
 * section the sphere spans is asked once, its answer kept in a grid, since
 * a thin band's walk steps between the two ends of each column and so
 * between sections at nearly every block.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
final class OreSections {

    private static final int SECTION_SHIFT = 4;
    private static final byte UNASKED = 0;
    private static final byte HOLDS_NONE = 1;
    private static final byte MAY_HOLD = 2;
    /** A run of blocks crossing sections of which only some may hold ore. */
    private static final byte PARTLY = 3;

    private final OreSource ores;
    private final int lowX;
    private final int lowY;
    private final int lowZ;
    private final int spanX;
    private final int spanY;
    private final int spanZ;
    private final byte[] answers;
    private final byte[] columnAnswers;

    /**
     * @param ores   the ore the walk can find
     * @param center the sphere's center
     * @param most   the radius the walk may reach at most
     */
    OreSections(OreSource ores, BlockPos center, double most) {
        this.ores = ores;
        int reach = (int) Math.ceil(most) + 1;
        lowX = (center.getX() - reach) >> SECTION_SHIFT;
        lowY = (center.getY() - reach) >> SECTION_SHIFT;
        lowZ = (center.getZ() - reach) >> SECTION_SHIFT;
        spanX = ((center.getX() + reach) >> SECTION_SHIFT) - lowX + 1;
        spanY = ((center.getY() + reach) >> SECTION_SHIFT) - lowY + 1;
        spanZ = ((center.getZ() + reach) >> SECTION_SHIFT) - lowZ + 1;
        answers = new byte[spanX * spanY * spanZ];
        columnAnswers = new byte[spanX * spanY];
    }

    /**
     * @param pos     a block
     * @param visited the blocks the walk already took into a vein
     * @return the block's ore, or null where it was visited or its section holds none
     */
    @Nullable Identifier oreAt(BlockPos pos, Set<BlockPos> visited) {
        return !mayHoldOre(pos.getX(), pos.getY(), pos.getZ()) || visited.contains(pos) ? null : ores.oreAt(pos);
    }

    /**
     * Hands an action each block of a band about a center that stands in a
     * section that may hold ore, in the order {@link AbilityMath#forEachInShell}
     * walks the band: column by column, each out from its foot both ways along
     * z. The block handed is one moving position, so an action keeps it only
     * through {@link BlockPos#immutable}.
     *
     * @param center the band's center
     * @param inner  the inner radius, exclusive
     * @param outer  the outer radius, inclusive
     * @param action what to do with each block
     */
    void forEachMayHoldInBand(BlockPos center, double inner, double outer, Consumer<BlockPos> action) {
        Band band = new Band(outer * outer, inner > 0 ? inner * inner : AbilityMath.HOLDS_THE_CENTER, action,
                new BlockPos.MutableBlockPos());
        int reach = (int) Math.floor(outer);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                int flat = dx * dx + dy * dy;
                int x = center.getX() + dx;
                int y = center.getY() + dy;
                if (flat <= band.outerSquared() && columnMayHold(x, y)) {
                    walkColumn(band, x, y, center.getZ(), flat);
                }
            }
        }
    }

    /**
     * The band one walk crosses and what it does with each block.
     *
     * @param outerSquared the outer radius squared, inclusive
     * @param innerSquared the inner radius squared, exclusive
     * @param action       what to do with each block
     * @param at           the one moving position handed to the action
     */
    private record Band(double outerSquared, double innerSquared, Consumer<BlockPos> action,
                        BlockPos.MutableBlockPos at) {

        boolean holds(int squared) {
            return squared > innerSquared && squared <= outerSquared;
        }
    }

    private void walkColumn(Band band, int x, int y, int footZ, int flat) {
        int farthest = (int) Math.floor(Math.sqrt(band.outerSquared() - flat));
        int nearest = band.innerSquared() < flat ? 0 : (int) Math.floor(Math.sqrt(band.innerSquared() - flat));
        int row = rowOf(x, y);
        byte ahead = sideOf(row, x, y, footZ + nearest, footZ + farthest);
        byte behind = sideOf(row, x, y, footZ - farthest, footZ - nearest);
        for (int dz = nearest; dz <= farthest; dz++) {
            if (band.holds(flat + dz * dz)) {
                visit(band, ahead, row, x, y, footZ + dz);
                if (dz != 0) {
                    visit(band, behind, row, x, y, footZ - dz);
                }
            }
        }
    }

    private void visit(Band band, byte side, int row, int x, int y, int z) {
        if (side == MAY_HOLD || side == PARTLY && mayHoldInRow(row, x, y, z)) {
            band.action().accept(band.at().set(x, y, z));
        }
    }

    /**
     * @param blockX a column's x
     * @param blockY a column's y
     * @return where the column's row of sections starts in the grid of answers
     */
    private int rowOf(int blockX, int blockY) {
        return ((blockX >> SECTION_SHIFT) - lowX) * spanY * spanZ + ((blockY >> SECTION_SHIFT) - lowY) * spanZ;
    }

    /**
     * @param blockX a column's x
     * @param blockY a column's y
     * @return true when any section along the column, within the sphere's sections, may hold ore
     */
    private boolean columnMayHold(int blockX, int blockY) {
        int index = ((blockX >> SECTION_SHIFT) - lowX) * spanY + ((blockY >> SECTION_SHIFT) - lowY);
        if (columnAnswers[index] == UNASKED) {
            columnAnswers[index] = anyAlong(rowOf(blockX, blockY), blockX, blockY) ? MAY_HOLD : HOLDS_NONE;
        }
        return columnAnswers[index] == MAY_HOLD;
    }

    private boolean anyAlong(int row, int blockX, int blockY) {
        for (int sectionZ = lowZ; sectionZ < lowZ + spanZ; sectionZ++) {
            if (mayHoldInRow(row, blockX, blockY, sectionZ << SECTION_SHIFT)) {
                return true;
            }
        }
        return false;
    }

    /**
     * How a run of blocks along one side of a column reads.
     *
     * @param row    the column's row of sections
     * @param blockX the column's x
     * @param blockY the column's y
     * @param fromZ  the run's first z
     * @param toZ    the run's last z
     * @return none of its sections may hold ore, every one may, or only some, each block then asked
     */
    private byte sideOf(int row, int blockX, int blockY, int fromZ, int toZ) {
        int holding = 0;
        int crossed = 0;
        for (int sectionZ = fromZ >> SECTION_SHIFT; sectionZ <= toZ >> SECTION_SHIFT; sectionZ++) {
            crossed++;
            if (mayHoldInRow(row, blockX, blockY, sectionZ << SECTION_SHIFT)) {
                holding++;
            }
        }
        if (holding == 0) {
            return HOLDS_NONE;
        }
        return holding == crossed ? MAY_HOLD : PARTLY;
    }

    /**
     * @param row    the column's row of sections
     * @param blockX the block's x
     * @param blockY the block's y
     * @param blockZ the block's z
     * @return true when the block's section may hold ore
     */
    private boolean mayHoldInRow(int row, int blockX, int blockY, int blockZ) {
        int index = row + (blockZ >> SECTION_SHIFT) - lowZ;
        if (answers[index] == UNASKED) {
            answers[index] = ores.mayHoldOre(blockX >> SECTION_SHIFT, blockY >> SECTION_SHIFT,
                    blockZ >> SECTION_SHIFT) ? MAY_HOLD : HOLDS_NONE;
        }
        return answers[index] == MAY_HOLD;
    }

    private boolean mayHoldOre(int blockX, int blockY, int blockZ) {
        boolean inGrid = within(blockX, lowX, spanX) && within(blockY, lowY, spanY);
        if (!inGrid || !within(blockZ, lowZ, spanZ)) {
            return ores.mayHoldOre(blockX >> SECTION_SHIFT, blockY >> SECTION_SHIFT, blockZ >> SECTION_SHIFT);
        }
        return mayHoldInRow(rowOf(blockX, blockY), blockX, blockY, blockZ);
    }

    private static boolean within(int block, int lowSection, int span) {
        int offset = (block >> SECTION_SHIFT) - lowSection;
        return offset >= 0 && offset < span;
    }
}
