package com.mercuriusxeno.goo.ability.frost;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;

/**
 * The blocks Rime goo freezes and thaws, warm and frozen: a water source
 * freezes to Rime ice and a lava source to obsidian while a Rime cell stands
 * in reach of it, and each goes back once none does.
 * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
 * iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen
 */
public enum RimeFreeze {

    /** A water source, freezing to Rime ice. */
    WATER,
    /** A lava source, freezing to obsidian. */
    LAVA,
    /** Rime ice, thawing to a water source. */
    ICE,
    /** Obsidian Rime froze, thawing to a lava source. */
    OBSIDIAN;

    /**
     * What a block becomes, given whether a Rime cell stands in reach of it:
     * warm in reach it freezes, frozen out of reach it thaws, and otherwise
     * it stands as it is.
     *
     * @param rimeInReach whether a Rime cell stands in reach of the block
     * @return the form it turns to, empty where it stands as it is
     */
    public Optional<RimeFreeze> settled(boolean rimeInReach) {
        return frozen() == rimeInReach ? Optional.empty() : Optional.of(counterpart());
    }

    /**
     * @return the form this one freezes or thaws to: water and Rime ice each other's, lava and obsidian each other's
     */
    private RimeFreeze counterpart() {
        return switch (this) {
            case WATER -> ICE;
            case ICE -> WATER;
            case LAVA -> OBSIDIAN;
            case OBSIDIAN -> LAVA;
        };
    }

    /**
     * @return true for the frozen forms, Rime ice and obsidian
     */
    public boolean frozen() {
        return this == ICE || this == OBSIDIAN;
    }

    /**
     * The form a block stands in: a water or lava source, Rime ice or
     * obsidian.
     *
     * @param state the block
     * @return its form, empty for any other block, flowing water and lava among them
     */
    static Optional<RimeFreeze> of(BlockState state) {
        if (state.is(GooBlocks.RIME_ICE.get())) {
            return Optional.of(ICE);
        }
        if (state.is(Blocks.OBSIDIAN)) {
            return Optional.of(OBSIDIAN);
        }
        if (!state.getFluidState().isSource()) {
            return Optional.empty();
        }
        if (state.is(Blocks.WATER)) {
            return Optional.of(WATER);
        }
        return state.is(Blocks.LAVA) ? Optional.of(LAVA) : Optional.empty();
    }

    /**
     * @return the block this form stands as
     */
    BlockState state() {
        return switch (this) {
            case WATER -> Blocks.WATER.defaultBlockState();
            case LAVA -> Blocks.LAVA.defaultBlockState();
            case ICE -> GooBlocks.RIME_ICE.get().defaultBlockState();
            case OBSIDIAN -> Blocks.OBSIDIAN.defaultBlockState();
        };
    }
}
