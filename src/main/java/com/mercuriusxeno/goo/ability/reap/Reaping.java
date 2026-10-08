package com.mercuriusxeno.goo.ability.reap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Reaps a ripe plant where it stands: a crop drops its loot less the seed it
 * keeps and stands again at age zero, a sweet berry bush drops its berries and
 * falls back to age one, a cave vine drops its glow berry and stands bare,
 * and cocoa drops its beans less the one it keeps and stands again at age
 * zero. The picking sound each plays is vanilla's own.
 * reap-breeze-harvests-and-replants
 */
public final class Reaping {

    /** Sweet berries a bush yields at age two and three, at the least, and the spread above that. */
    private static final int BERRIES_AT_AGE_TWO = 1;
    private static final int BERRIES_AT_FULL = 2;
    private static final int BERRY_SPREAD = 2;
    /** The seeds a replanted crop or pod is stood again from. */
    private static final int SEEDS_REPLANTED = 1;
    private static final float PICK_VOLUME = 1.0f;
    private static final float PICK_PITCH_LOW = 0.8f;
    private static final float PICK_PITCH_SPREAD = 0.4f;

    private Reaping() {
    }

    /**
     * The kind of plant a block is, where Reap harvests it.
     *
     * @param state the block
     * @return its kind, or null for a block Reap leaves alone
     */
    public static @Nullable RipeKind kindOf(BlockState state) {
        if (state.getBlock() instanceof CropBlock) {
            return RipeKind.CROP;
        }
        if (state.getBlock() instanceof SweetBerryBushBlock) {
            return RipeKind.SWEET_BERRIES;
        }
        if (state.hasProperty(CaveVines.BERRIES)) {
            return RipeKind.GLOW_BERRIES;
        }
        return state.getBlock() instanceof CocoaBlock ? RipeKind.COCOA : null;
    }

    /**
     * Whether a block is a plant Reap harvests now.
     *
     * @param state the block
     * @return true for a ripe plant
     */
    public static boolean ripe(BlockState state) {
        RipeKind kind = kindOf(state);
        return kind != null && ripeAs(kind, state);
    }

    /**
     * Whether a plant of a known kind is ripe, read off its own age or berries.
     *
     * @param kind  the plant's kind
     * @param state the plant
     * @return true for a ripe plant
     */
    private static boolean ripeAs(RipeKind kind, BlockState state) {
        return switch (kind) {
            case CROP -> kind.ripe(((CropBlock) state.getBlock()).getAge(state),
                    ((CropBlock) state.getBlock()).getMaxAge(), false);
            case SWEET_BERRIES -> kind.ripe(state.getValue(SweetBerryBushBlock.AGE), SweetBerryBushBlock.MAX_AGE,
                    false);
            case GLOW_BERRIES -> kind.ripe(0, 0, state.getValue(CaveVines.BERRIES));
            case COCOA -> kind.ripe(state.getValue(CocoaBlock.AGE), CocoaBlock.MAX_AGE, false);
        };
    }

    /**
     * Reaps the ripe plant in a cell; a cell holding none is left as it is.
     *
     * @param level the server level
     * @param pos   the cell
     * @return true when a plant was reaped
     */
    public static boolean reap(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        RipeKind kind = kindOf(state);
        if (kind == null || !ripeAs(kind, state)) {
            return false;
        }
        harvest(level, pos, state, kind);
        return true;
    }

    /**
     * Harvests a ripe plant of a known kind.
     *
     * @param level the server level
     * @param pos   the plant's cell
     * @param state the ripe plant
     * @param kind  its kind
     */
    private static void harvest(ServerLevel level, BlockPos pos, BlockState state, RipeKind kind) {
        RandomSource random = level.getRandom();
        switch (kind) {
            case CROP -> replant(level, pos, state, ((CropBlock) state.getBlock()).getStateForAge(0));
            case SWEET_BERRIES -> pick(level, pos, state.setValue(SweetBerryBushBlock.AGE, 1),
                    new ItemStack(Items.SWEET_BERRIES, sweetBerries(state.getValue(SweetBerryBushBlock.AGE), random)));
            case GLOW_BERRIES -> pick(level, pos, state.setValue(CaveVines.BERRIES, false),
                    new ItemStack(Items.GLOW_BERRIES));
            case COCOA -> replant(level, pos, state, state.setValue(CocoaBlock.AGE, 0));
        }
    }

    /**
     * The sweet berries a ripe bush yields, as vanilla's pick does.
     *
     * @param age    the bush's age
     * @param random the random source
     * @return the berries
     */
    static int sweetBerries(int age, RandomSource random) {
        int least = age >= SweetBerryBushBlock.MAX_AGE ? BERRIES_AT_FULL : BERRIES_AT_AGE_TWO;
        return least + random.nextInt(BERRY_SPREAD);
    }

    /**
     * Drops a plant's loot less the seed it is replanted from, and stands it again young.
     *
     * @param level the server level
     * @param pos   the plant's cell
     * @param ripe  the ripe plant
     * @param young the plant replanted
     */
    public static void replant(ServerLevel level, BlockPos pos, BlockState ripe, BlockState young) {
        List<ItemStack> drops = Block.getDrops(ripe, level, pos, level.getBlockEntity(pos));
        settleSeeds(drops, ripe.getBlock().asItem(), SEEDS_REPLANTED);
        drops.forEach(stack -> Block.popResource(level, pos, stack));
        level.setBlock(pos, young, Block.UPDATE_ALL);
        playPick(level, pos, ripe.getSoundType(level, pos, null).getBreakSound());
    }

    /**
     * Settles the seeds a plant dropped against the seeds it was replanted
     * from: every seed across the drops is tallied, the replanted ones are
     * paid out of the tally, and what is left drops, never fewer than none.
     * reap-breeze-harvests-and-replants
     *
     * @param drops     the plant's drops, settled in place
     * @param seed      the item its seed is
     * @param replanted the seeds the replant took
     */
    public static void settleSeeds(List<ItemStack> drops, Item seed, int replanted) {
        int dropped = drops.stream().filter(stack -> stack.is(seed)).mapToInt(ItemStack::getCount).sum();
        drops.removeIf(stack -> stack.is(seed) || stack.isEmpty());
        int left = seedsLeft(dropped, replanted);
        int perStack = new ItemStack(seed).getMaxStackSize();
        while (left > 0) {
            int count = Math.min(left, perStack);
            drops.add(new ItemStack(seed, count));
            left -= count;
        }
    }

    /**
     * The seeds left to drop once the replanted ones are paid out of those
     * dropped, clamped at none.
     *
     * @param dropped   the seeds the plant dropped
     * @param replanted the seeds the replant took
     * @return the seeds left to drop
     */
    static int seedsLeft(int dropped, int replanted) {
        return Math.max(0, dropped - replanted);
    }

    private static void pick(ServerLevel level, BlockPos pos, BlockState picked, ItemStack berries) {
        Block.popResource(level, pos, berries);
        level.setBlock(pos, picked, Block.UPDATE_ALL);
        playPick(level, pos, picked.hasProperty(CaveVines.BERRIES) ? SoundEvents.CAVE_VINES_PICK_BERRIES
                : SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES);
    }

    private static void playPick(ServerLevel level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, PICK_VOLUME,
                PICK_PITCH_LOW + level.getRandom().nextFloat() * PICK_PITCH_SPREAD);
    }
}
