package com.mercuriusxeno.goo.ability.ornate;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.FortuneStep;
import com.mercuriusxeno.goo.block.ability.CellBinding;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.enchanting.EnchantedBlockLootEvent;
import net.neoforged.neoforge.event.enchanting.EnchantedEntityLootEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

/**
 * Raises a loot roll's level by the Fortune goo against what drops: a
 * block's Fortune by the fullest Fortune cell holding on one of its faces,
 * a killed mob's Looting by the Fortune cell it stands in, added to what
 * the tool or weapon carries. Each block or mob that drops more spends one
 * layer of its cell, once, however many of its loot's entries read the level.
 * fortune-is-ornates-residual-that-makes-things-drop-more
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class FortuneEvents {

    private static final DropGrants GRANTS = new DropGrants();

    private FortuneEvents() {
    }

    /**
     * Adds the Fortune goo's level to a block loot roll reading Fortune.
     *
     * @param event the block loot event
     */
    @SubscribeEvent
    public static void onBlockLoot(EnchantedBlockLootEvent event) {
        if (!event.getEnchantment().is(Enchantments.FORTUNE) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos broken = event.getPos().immutable();
        fortuneOf(level).ifPresent(step -> event.setEnchantmentLevel(event.getEnchantmentLevel()
                + GRANTS.grant(level.getGameTime(), List.of(level.dimension(), broken),
                        () -> breakBonus(level, broken, step))));
    }

    /**
     * Adds the Fortune goo's level to a mob loot roll reading Looting.
     *
     * @param event the entity loot event
     */
    @SubscribeEvent
    public static void onEntityLoot(EnchantedEntityLootEvent event) {
        LivingEntity mob = event.getEntity();
        if (!event.getEnchantment().is(Enchantments.LOOTING) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        fortuneOf(level).ifPresent(step -> event.setEnchantmentLevel(event.getEnchantmentLevel()
                + GRANTS.grant(level.getGameTime(), mob.getUUID(), () -> killBonus(level, mob, step))));
    }

    private static int breakBonus(ServerLevel level, BlockPos broken, FortuneStep step) {
        Block cell = GooBlocks.ORNATE_FORTUNE.get();
        ToIntFunction<Direction> onFace = face -> FortuneLevels.layersOnFace(
                level.getBlockState(broken.relative(face)), face, cell);
        int bonus = FortuneLevels.forBreak(onFace, step);
        if (bonus > 0) {
            FortuneLevels.fullestFace(onFace).ifPresent(face -> spendALayer(level, broken.relative(face)));
        }
        return bonus;
    }

    private static int killBonus(ServerLevel level, LivingEntity mob, FortuneStep step) {
        return cellStoodIn(level, mob.blockPosition()).map(pos -> {
            int bonus = FortuneLevels.ofLayers(level.getBlockState(pos).getValue(VolumeCellBlock.LAYERS), step);
            if (bonus > 0) {
                spendALayer(level, pos);
            }
            return bonus;
        }).orElse(0);
    }

    /**
     * The Fortune cell a mob stands in: the one at its feet, or the full one under them it stands on.
     *
     * @param level the level
     * @param feet  the block at the mob's feet
     * @return the cell's place, empty where the mob stands in no Fortune goo
     */
    private static Optional<BlockPos> cellStoodIn(ServerLevel level, BlockPos feet) {
        Block cell = GooBlocks.ORNATE_FORTUNE.get();
        if (level.getBlockState(feet).is(cell)) {
            return Optional.of(feet);
        }
        return level.getBlockState(feet.below()).is(cell) ? Optional.of(feet.below()) : Optional.empty();
    }

    private static void spendALayer(ServerLevel level, BlockPos pos) {
        BlockState cell = level.getBlockState(pos);
        int layers = cell.getValue(VolumeCellBlock.LAYERS);
        level.setBlock(pos, layers > 1 ? cell.setValue(VolumeCellBlock.LAYERS, layers - 1)
                : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static Optional<FortuneStep> fortuneOf(ServerLevel level) {
        return Optional.ofNullable(AbilityRegistry.of(level).getAbility(CellBinding.FORTUNE_ABILITY))
                .map(AbilityDefinition::behaviors).flatMap(FortuneStep::in);
    }

    /**
     * The bonus each drop took this game tick, so a loot table reading the
     * level in several entries rolls each at one bonus and its cell spends once.
     */
    static final class DropGrants {

        private long tick = Long.MIN_VALUE;
        private final Map<Object, Integer> granted = new HashMap<>();

        /**
         * The bonus a drop takes, rolled the first time the drop asks this tick.
         *
         * @param now       the game tick
         * @param drop      what drops: a block's place or a mob's id
         * @param firstRoll reads the bonus and spends the cell's layer
         * @return the drop's bonus
         */
        int grant(long now, Object drop, IntSupplier firstRoll) {
            if (now != tick) {
                tick = now;
                granted.clear();
            }
            Integer bonus = granted.get(drop);
            if (bonus == null) {
                bonus = firstRoll.getAsInt();
                granted.put(drop, bonus);
            }
            return bonus;
        }
    }
}
