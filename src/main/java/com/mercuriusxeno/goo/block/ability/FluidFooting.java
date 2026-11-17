package com.mercuriusxeno.goo.block.ability;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * The fluid sources a world goo's drips stop at and its cell lies on, where
 * every other fluid passes a drip through to the block beneath: none for most
 * goo, lava for Molten, which pools on it, and water and lava both for Rime,
 * which freezes what it lands on so its sprayer can cross.
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 * rime-drips-stop-at-a-water-or-lava-surface
 */
public enum FluidFooting {

    /** No fluid: drips pass through every fluid to the block beneath. */
    NONE(false, false),
    /** Lava sources: Molten's drips pool on them and pass through water. */
    LAVA(false, true),
    /** Water and lava sources: Rime's drips stop on both. */
    WATER_AND_LAVA(true, true);

    private final boolean water;
    private final boolean lava;

    FluidFooting(boolean water, boolean lava) {
        this.water = water;
        this.lava = lava;
    }

    /**
     * @return true where drips stop at some fluid, so their flight clips fluid sources
     */
    public boolean stopsAtAnyFluid() {
        return water || lava;
    }

    /**
     * Whether a drip flying through a fluid stops at its surface.
     *
     * @param fluid the fluid the drip meets
     * @return true for a water or lava source this footing names
     */
    public boolean stopsAt(FluidState fluid) {
        return fluid.isSource() && names(fluid);
    }

    /**
     * @param fluid the fluid
     * @return true where the fluid is water or lava and this footing names it
     */
    private boolean names(FluidState fluid) {
        return water && fluid.is(FluidTags.WATER) || lava && fluid.is(FluidTags.LAVA);
    }

    /**
     * Whether a cell lies on a block: one holding a water or lava source this
     * footing names, read by the fluid's tag, so Molten lies on molten iron
     * as on lava.
     *
     * @param footing the block under the cell
     * @return true where the cell lies on it as on a sturdy top
     */
    public boolean liesOn(BlockState footing) {
        return stopsAt(footing.getFluidState());
    }
}
