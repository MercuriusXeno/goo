package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityMath;
import com.mercuriusxeno.goo.entity.CompressedHoard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The world side of {@link HoardHost}: walks a sphere of blocks, taking each
 * breakable one into the hoard as a netherite pickaxe with silk touch would
 * drop it, and draws item entities in until they reach the anchor
 * (decision black-hole-leaves-a-compression-sphere).
 */
final class HoardedBlocks {

    /** How near the anchor's center a pulled item must come to be taken. */
    private static final double TAKE_REACH = 1.5;

    private HoardedBlocks() {
    }

    /**
     * Takes the breakable blocks within a sphere, leaving its center, the
     * anchor's own cell. Air, a fluid and an unbreakable block stand.
     *
     * @param level  the level to take from
     * @param center the sphere center, which the walk skips
     * @param radius the sphere radius in whole blocks
     * @param hoard  the hoard each block's drops join
     */
    static void takeSphere(ServerLevel level, BlockPos center, int radius, CompressedHoard hoard) {
        ItemStack silkPick = silkPick(level);
        AbilityMath.forEachInSphere(center, radius, target -> {
            if (!target.equals(center)) {
                takeIfBreakable(level, target, silkPick, hoard);
            }
        });
    }

    private static ItemStack silkPick(ServerLevel level) {
        ItemStack pick = new ItemStack(Items.NETHERITE_PICKAXE);
        pick.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        return pick;
    }

    private static void takeIfBreakable(ServerLevel level, BlockPos target, ItemStack silkPick, CompressedHoard hoard) {
        BlockState state = level.getBlockState(target);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getDestroySpeed(level, target) < 0) {
            return;
        }
        Block.getDrops(state, level, target, level.getBlockEntity(target), null, silkPick).forEach(hoard::add);
        level.removeBlock(target, false);
    }

    /**
     * Pushes each item entity in a sphere toward its center, and takes any
     * within reach of the center into the hoard, removing it.
     *
     * @param level  the level to scan
     * @param center the sphere center
     * @param radius the sphere radius in blocks
     * @param speed  the velocity added toward the center, in blocks per tick
     * @param hoard  the hoard taken items join
     */
    static void pullItems(ServerLevel level, Vec3 center, double radius, double speed, CompressedHoard hoard) {
        double radiusSquared = radius * radius;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(radius),
                item -> item.isAlive() && item.distanceToSqr(center) <= radiusSquared)) {
            Vec3 toward = center.subtract(item.position());
            double distance = toward.length();
            if (distance <= TAKE_REACH) {
                hoard.add(item.getItem());
                item.discard();
            } else {
                item.setDeltaMovement(item.getDeltaMovement().add(toward.scale(speed / distance)));
                item.hurtMarked = true;
            }
        }
    }
}
