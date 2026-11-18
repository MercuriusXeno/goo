package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every kind of thing that can act on a Nitro cell, each beside what the
 * cell answers it: the one list of what sets Nitro off and what spares it,
 * which the cell reads and the world goo reactions coming later extend.
 * Anything that touches it, heats it, shocks it, pulses it or strikes it
 * sets it off; Vacuum and an unstable goo item spare it, so the goo can be
 * reclaimed safely.
 * nitro-explodes-when-touched-heated-shocked-pulsed-or-struck
 * vacuum-and-unstable-goo-items-do-not-set-nitro-off
 */
public enum NitroContact {
    /** Any entity touching the cell or stepping on it, the one exception aside. */
    TOUCH(NitroReaction.DETONATE),
    /** Fire, lava, a burning thing or Molten goo beside it, or Flames' stream on it. */
    HEAT(NitroReaction.DETONATE),
    /** Zap landing on it, or lightning striking by it. */
    SHOCK(NitroReaction.DETONATE),
    /** A redstone signal reaching it, Tick and Signal goo among the sources. */
    PULSE(NitroReaction.DETONATE),
    /** A hit with a tool or a fist, an attempt to break it, or a projectile landing on it. */
    STRIKE(NitroReaction.DETONATE),
    /** Vacuum taking the goo up, which returns it as it does any residual. */
    VACUUM(NitroReaction.HOLD),
    /** An unstable goo item, the dropped item entity, resting on or falling into it. */
    UNSTABLE_GOO_ITEM(NitroReaction.HOLD);

    private final NitroReaction reaction;

    NitroContact(NitroReaction reaction) {
        this.reaction = reaction;
    }

    /**
     * @return what the cell does when this acts on it
     */
    public NitroReaction reaction() {
        return reaction;
    }

    /**
     * The kind of contact an entity touching the cell makes: an unstable goo
     * item spares it, a projectile strikes it, a burning thing heats it, and
     * anything else touches it.
     *
     * @param entity the entity touching the cell
     * @return the contact it makes
     */
    public static NitroContact madeBy(Entity entity) {
        if (entity instanceof ItemEntity item && GooTypes.UNSTABLE.equals(GooStacks.keyOf(item.getItem()))) {
            return UNSTABLE_GOO_ITEM;
        }
        if (entity instanceof Projectile) {
            return STRIKE;
        }
        return entity.isOnFire() ? HEAT : TOUCH;
    }

    /**
     * Whether a block beside the cell heats it: fire, lava, magma, a lit
     * campfire or Blaze's Molten goo.
     *
     * @param beside the block beside the cell
     * @return true where it heats the cell
     */
    public static boolean heats(BlockState beside) {
        return burns(beside) || beside.is(Blocks.MAGMA_BLOCK) || beside.getBlock() instanceof MoltenCellBlock;
    }

    private static boolean burns(BlockState beside) {
        return beside.is(BlockTags.FIRE) || beside.getFluidState().is(FluidTags.LAVA)
                || CampfireBlock.isLitCampfire(beside);
    }
}
