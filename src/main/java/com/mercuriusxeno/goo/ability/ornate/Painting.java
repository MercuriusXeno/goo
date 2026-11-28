package com.mercuriusxeno.goo.ability.ornate;

import com.mercuriusxeno.goo.ability.program.PlacementPrice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * Paints one thing a color, paying the hold's price for it first: a
 * dyeable block becomes its family's block of the color with its state and
 * contents kept, both halves of a bed together, and a sheep's wool or a
 * tamed wolf's or cat's collar takes the color. A thing already that color,
 * or one no dye colors, is left alone and costs nothing.
 * paint-recolors-dyeable-things-to-the-off-hand-dye
 */
public final class Painting {

    /** The one thing a price pays for. */
    private static final int ONE_THING = 1;
    /**
     * Sends the swap to clients without telling the neighbours, so a bed's
     * half sees no partner missing while the other half swaps, and keeps the
     * old block entity's contents in the world, not spilled.
     */
    private static final int SWAP_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    private Painting() {
    }

    /**
     * Paints the block at a place, where a dye colors it and it stands another color.
     *
     * @param level the level
     * @param pos   the block
     * @param color the dye's color
     * @param price the hold's price, paid once for the block
     * @return true where the block was painted
     */
    public static boolean paintBlock(ServerLevel level, BlockPos pos, DyeColor color, PlacementPrice price) {
        BlockState state = level.getBlockState(pos);
        Optional<Block> painted = PaintedColors.recolored(BuiltInRegistries.BLOCK.getKey(state.getBlock()), color)
                .flatMap(BuiltInRegistries.BLOCK::getOptional).filter(block -> block != state.getBlock());
        if (painted.isEmpty() || !price.pay(ONE_THING)) {
            return false;
        }
        if (state.getBlock() instanceof BedBlock) {
            BlockPos other = bedPartnerOf(pos, state);
            BlockState otherState = level.getBlockState(other);
            if (otherState.is(state.getBlock())) {
                swap(level, other, otherState, painted.get());
            }
        }
        swap(level, pos, state, painted.get());
        return true;
    }

    /**
     * Paints a sheep's wool or a tamed wolf's or cat's collar, where it stands another color.
     *
     * @param mob   the living thing
     * @param color the dye's color
     * @param price the hold's price, paid once for the mob
     * @return true where the mob was painted
     */
    public static boolean paintMob(LivingEntity mob, DyeColor color, PlacementPrice price) {
        @Nullable DataComponentType<DyeColor> dyed = dyedPartOf(mob);
        if (dyed == null || mob.get(dyed) == color || !price.pay(ONE_THING)) {
            return false;
        }
        mob.setComponent(dyed, color);
        return true;
    }

    private static @Nullable DataComponentType<DyeColor> dyedPartOf(LivingEntity mob) {
        if (mob instanceof Sheep) {
            return DataComponents.SHEEP_COLOR;
        }
        if (mob instanceof TamableAnimal tamable && tamable.isTame()) {
            if (mob instanceof Wolf) {
                return DataComponents.WOLF_COLLAR;
            }
            if (mob instanceof Cat) {
                return DataComponents.CAT_COLLAR;
            }
        }
        return null;
    }

    private static BlockPos bedPartnerOf(BlockPos pos, BlockState bed) {
        return bed.getValue(BedBlock.PART) == BedPart.FOOT ? pos.relative(bed.getValue(BedBlock.FACING))
                : pos.relative(bed.getValue(BedBlock.FACING).getOpposite());
    }

    /**
     * Swaps a block for another, keeping every state property they share and the block entity's contents.
     *
     * @param level   the level
     * @param pos     the block
     * @param state   the block's state before the swap
     * @param painted the block it becomes
     */
    private static void swap(ServerLevel level, BlockPos pos, BlockState state, Block painted) {
        BlockEntity old = level.getBlockEntity(pos);
        @Nullable CompoundTag contents = old == null ? null : old.saveCustomOnly(level.registryAccess());
        level.setBlock(pos, painted.withPropertiesOf(state), SWAP_FLAGS);
        BlockEntity fresh = level.getBlockEntity(pos);
        if (contents != null && fresh != null) {
            fresh.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), contents));
            fresh.setChanged();
            BlockState placed = level.getBlockState(pos);
            level.sendBlockUpdated(pos, placed, placed, Block.UPDATE_CLIENTS);
        }
    }
}
