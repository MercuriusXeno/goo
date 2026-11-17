package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Who stands in Leech goo, read alike by the server's drain and the client's
 * wisps: a slowing cell filling the block a living thing's feet are in, or
 * else one holding it up, so the mobs shedding wisps are the mobs a strike
 * drains.
 * hex-wisps-drift-off-mobs-standing-in-leech-goo
 * damage-dealt-to-a-mob-in-leech-goo-becomes-drained-life-for-its-source
 */
public final class LeechStanding {

    private LeechStanding() {
    }

    /**
     * The Leech cell a living thing stands in, from the block its feet are in
     * and the block holding it up.
     *
     * @param feet       the block the feet are in
     * @param underFeet  the block holding the thing up
     * @param holdsLeech whether a block holds a Leech cell
     * @return the cell, the feet's block first, empty where neither holds Leech goo
     */
    static Optional<BlockPos> leechCellUnder(BlockPos feet, BlockPos underFeet, Predicate<BlockPos> holdsLeech) {
        return Stream.of(feet, underFeet).filter(holdsLeech).findFirst();
    }

    /**
     * The Leech cell a living thing stands in, read from its level.
     *
     * @param living the living thing
     * @return the cell, empty where no Leech goo caught it
     */
    public static Optional<BlockPos> leechCellUnder(LivingEntity living) {
        Level level = living.level();
        return leechCellUnder(living.blockPosition(), living.getOnPos(),
                pos -> VolumeCellBlock.hasSurface(level.getBlockState(pos).getBlock(), CellSurface.SLOW));
    }

    /**
     * The leech goo step the ability that laid a Leech cell runs, from the
     * server's registry or the client's synced abilities.
     *
     * @param level the level
     * @param cell  the Leech cell
     * @return the step, empty where the cell is gone or its ability is not loaded
     */
    public static Optional<LeechGooStep> stepAt(Level level, BlockPos cell) {
        return level.getBlockState(cell).getBlock() instanceof VolumeCellBlock leechCell
                ? LeechGooStep.in(leechCell.layingProgram(level)) : Optional.empty();
    }
}
