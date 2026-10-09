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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The world side of {@link HoardHost}: walks a sphere of blocks core outward,
 * a budget of them a tick, taking each breakable one into the hoard as a
 * netherite pickaxe with silk touch would drop it, and draws item entities
 * in until they reach the anchor (decision black-hole-leaves-a-compression-sphere).
 */
final class HoardedBlocks {

    /** How near the anchor's center a pulled item must come to be taken. */
    private static final double TAKE_REACH = 1.5;

    private HoardedBlocks() {
    }

    /**
     * The cells of a sphere a black hole takes, its center, the anchor's own
     * cell, left out, nearest the center first, so the hole decays away its
     * core outward (decision black-hole-leaves-a-compression-sphere).
     *
     * @param center the sphere center
     * @param radius the sphere radius in whole blocks
     * @return the cells, nearest first
     */
    static List<BlockPos> coreOutward(BlockPos center, int radius) {
        List<BlockPos> cells = new ArrayList<>();
        AbilityMath.forEachInSphere(center, radius, cell -> {
            if (!cell.equals(center)) {
                cells.add(cell.immutable());
            }
        });
        cells.sort(Comparator.comparingDouble(cell -> cell.distSqr(center)));
        return cells;
    }

    /**
     * Takes the next cells of a sphere into the hoard, up to a budget, each
     * breakable block as its silk-touched drops; air, a fluid and an
     * unbreakable block stand.
     *
     * @param level  the level to take from
     * @param cells  the sphere's cells, nearest the center first
     * @param from   the index of the first cell not yet taken
     * @param budget the most cells this call takes
     * @param hoard  the hoard each block's drops join
     * @return the index of the first cell still to take, the cell count once every cell is taken
     */
    static int takeSome(ServerLevel level, List<BlockPos> cells, int from, int budget, CompressedHoard hoard) {
        ItemStack silkPick = silkPick(level);
        int to = Math.min(cells.size(), from + budget);
        for (int index = from; index < to; index++) {
            takeIfBreakable(level, cells.get(index), silkPick, hoard);
        }
        return to;
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
