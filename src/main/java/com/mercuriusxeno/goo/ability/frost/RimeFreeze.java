package com.mercuriusxeno.goo.ability.frost;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;

/**
 * The blocks Rime goo freezes, warm and frozen: a water source freezes to
 * Rime ice and a lava source to obsidian, and what stands frozen stays so,
 * the change running one way, so the water or lava under someone crossing on
 * it never comes back.
 * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
 * rime-freezing-is-permanent-with-no-restore-on-removal
 */
public enum RimeFreeze {

    /** A water source, freezing to Rime ice. */
    WATER,
    /** A lava source, freezing to obsidian. */
    LAVA,
    /** Rime ice, standing frozen for good. */
    ICE,
    /** Obsidian, standing frozen for good. */
    OBSIDIAN;

    /**
     * What a block freezes to.
     *
     * @return Rime ice for water, obsidian for lava, empty for what already stands frozen
     */
    public Optional<RimeFreeze> freeze() {
        return switch (this) {
            case WATER -> Optional.of(ICE);
            case LAVA -> Optional.of(OBSIDIAN);
            case ICE, OBSIDIAN -> Optional.empty();
        };
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
