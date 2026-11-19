package com.mercuriusxeno.goo.ability.program;

import com.google.common.base.Suppliers;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The mycelial blocks Transmit travels through: the sculk family, mycelium
 * and Creep cells, sculk goo itself.
 * transmit-reaches-only-connected-mycelial-blocks-you-touch
 */
public final class MycelialBlocks {

    /** Read once the registries stand, since the Creep cell is goo's own block. */
    private static final Supplier<Set<Block>> MYCELIAL = Suppliers.memoize(() -> Set.of(
            Blocks.SCULK, Blocks.SCULK_VEIN, Blocks.SCULK_CATALYST, Blocks.SCULK_SENSOR,
            Blocks.CALIBRATED_SCULK_SENSOR, Blocks.SCULK_SHRIEKER, Blocks.MYCELIUM, GooBlocks.SCULK_CREEP.get()));

    private MycelialBlocks() {
    }

    /**
     * Whether a block is mycelial.
     *
     * @param state the block
     * @return true for the sculk family, mycelium and a Creep cell
     */
    public static boolean is(BlockState state) {
        return MYCELIAL.get().contains(state.getBlock());
    }

    /**
     * Which places of a level hold a mycelial block; a place in a chunk the
     * level holds unloaded reads as none, so a question never loads a chunk.
     *
     * @param level the level
     * @return whether a place holds a mycelial block
     */
    public static Predicate<BlockPos> in(Level level) {
        return pos -> level.isLoaded(pos) && is(level.getBlockState(pos));
    }
}
