package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.ornate.FortuneLevels;
import com.mercuriusxeno.goo.ability.program.FortuneStep;
import com.mercuriusxeno.goo.block.ability.CellBinding;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.enchanting.EnchantedBlockLootEvent;
import net.neoforged.neoforge.event.enchanting.EnchantedEntityLootEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Ornate's Fortune as a world goo through the real paths: a zombie killed
 * standing in a Fortune cell rolls its loot at the looting level the cell's
 * layers set, and an ore broken by a survival player under a Fortune cell
 * rolls at that fortune level, each cell spending one layer for it.
 * fortune-is-ornates-residual-that-makes-things-drop-more
 */
@RegistersGameTests
public final class FortuneTests {

    private static final String KILL_IN_FORTUNE = "a_kill_in_fortune_rolls_looting_and_spends_a_layer";
    private static final String BREAK_UNDER_FORTUNE = "an_ore_broken_under_fortune_rolls_fortune_and_spends_a_layer";
    private static final String ABILITY_REQUIRED = "The ability registry must hold ornate_fortune";
    private static final BlockPos FLOOR_MIDDLE = new BlockPos(2, 1, 2);
    private static final int BAY_EDGE = 4;
    private static final int BAY_TOP = 5;
    /** Six layers, the second level's first. */
    private static final int KILL_LAYERS = 6;
    /** Eleven layers, the third level's first. */
    private static final int BREAK_LAYERS = 11;
    private static final String LEVEL_WRONG = "The %s should roll at level %d, the seam's answer for %d layers; "
            + "it rolled at %s";
    private static final String CELL_LAYERS_WRONG = "The Fortune cell should hold %d layers after the %s, holds %d";
    private static final String BAY_LAYERS_WRONG = "The bay should hold %d layers of Fortune goo once the fall "
            + "lands, holds %d";
    /** A second and a half, the cell's fall from the broken ore's top onto the stone with room to spare. */
    private static final int FALL_TICKS = 30;

    private FortuneTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, KILL_IN_FORTUNE, FortuneTests::aKillInFortuneRollsLootingAndSpendsALayer);
        GooTestFunctions.registerTest(registrar, BREAK_UNDER_FORTUNE,
                FortuneTests::anOreBrokenUnderFortuneRollsFortuneAndSpendsALayer);
    }

    /**
     * A zombie standing in a Fortune cell of 6 layers is killed: its loot
     * rolls looting at the level FortuneLevels answers for 6 layers, and the
     * cell holds 5 at once after.
     *
     * @param helper the gametest helper
     */
    public static void aKillInFortuneRollsLootingAndSpendsALayer(GameTestHelper helper) {
        clearABay(helper);
        helper.setBlock(FLOOR_MIDDLE, GooBlocks.ORNATE_FORTUNE.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, KILL_LAYERS));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(FLOOR_MIDDLE));
        int expected = FortuneLevels.ofLayers(KILL_LAYERS, fortuneOf(helper));
        LootLevels rolled = new LootLevels(helper, null);
        NeoForge.EVENT_BUS.register(rolled);
        try {
            zombie.kill(helper.getLevel());
        } finally {
            NeoForge.EVENT_BUS.unregister(rolled);
        }
        helper.assertTrue(rolled.looting.isPresent() && rolled.looting.getAsInt() == expected,
                String.format(LEVEL_WRONG, "kill", expected, KILL_LAYERS, rolled.looting));
        assertCellHolds(helper, FLOOR_MIDDLE, KILL_LAYERS - 1, "kill");
        helper.succeed();
    }

    /**
     * A survival player with a bare iron pickaxe breaks a coal ore under a
     * Fortune cell of 11 layers: the ore's loot rolls fortune at the level
     * FortuneLevels answers for 11 layers, and the cell spends one: it has
     * lost its floor, so it falls onto the stone below, and once the fall
     * lands the bay holds 10 layers of Fortune goo.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void anOreBrokenUnderFortuneRollsFortuneAndSpendsALayer(GameTestHelper helper) {
        clearABay(helper);
        BlockPos above = FLOOR_MIDDLE.above();
        helper.setBlock(FLOOR_MIDDLE, Blocks.COAL_ORE);
        helper.setBlock(above, GooBlocks.ORNATE_FORTUNE.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, BREAK_LAYERS));
        int expected = FortuneLevels.ofLayers(BREAK_LAYERS, fortuneOf(helper));
        ServerPlayer miner = helper.makeMockServerPlayerInLevel();
        miner.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        LootLevels rolled = new LootLevels(helper, helper.absolutePos(FLOOR_MIDDLE));
        NeoForge.EVENT_BUS.register(rolled);
        try {
            miner.gameMode.destroyBlock(helper.absolutePos(FLOOR_MIDDLE));
        } finally {
            NeoForge.EVENT_BUS.unregister(rolled);
            helper.getLevel().getServer().getPlayerList().remove(miner);
        }
        helper.assertTrue(rolled.fortune.isPresent() && rolled.fortune.getAsInt() == expected,
                String.format(LEVEL_WRONG, "ore", expected, BREAK_LAYERS, rolled.fortune));
        helper.runAfterDelay(FALL_TICKS, () -> {
            int held = fortuneLayersInTheBay(helper);
            helper.assertTrue(held == BREAK_LAYERS - 1,
                    String.format(BAY_LAYERS_WRONG, BREAK_LAYERS - 1, held));
            helper.succeed();
        });
    }

    /** Every layer of Fortune goo standing in the bay, wherever its cells came to rest. */
    private static int fortuneLayersInTheBay(GameTestHelper helper) {
        int held = 0;
        for (int x = 0; x <= BAY_EDGE; x++) {
            for (int z = 0; z <= BAY_EDGE; z++) {
                for (int y = 1; y <= BAY_TOP; y++) {
                    BlockState cell = helper.getBlockState(new BlockPos(x, y, z));
                    held += cell.is(GooBlocks.ORNATE_FORTUNE.get()) ? cell.getValue(VolumeCellBlock.LAYERS) : 0;
                }
            }
        }
        return held;
    }

    private static void clearABay(GameTestHelper helper) {
        for (int x = 0; x <= BAY_EDGE; x++) {
            for (int z = 0; z <= BAY_EDGE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= BAY_TOP; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static FortuneStep fortuneOf(GameTestHelper helper) {
        AbilityDefinition fortune = AbilityRegistry.of(helper.getLevel()).getAbility(CellBinding.FORTUNE_ABILITY);
        helper.assertTrue(fortune != null, ABILITY_REQUIRED);
        return FortuneStep.in(fortune.behaviors()).orElseThrow();
    }

    private static void assertCellHolds(GameTestHelper helper, BlockPos pos, int layers, String what) {
        BlockState cell = helper.getBlockState(pos);
        int held = cell.is(GooBlocks.ORNATE_FORTUNE.get()) ? cell.getValue(VolumeCellBlock.LAYERS) : 0;
        helper.assertTrue(held == layers, String.format(CELL_LAYERS_WRONG, layers, what, held));
    }

    /**
     * The fortune and looting levels a loot roll ends on, read last, after
     * every other listener set them.
     */
    public static final class LootLevels {

        private final GameTestHelper helper;
        private final BlockPos ore;
        private OptionalInt fortune = OptionalInt.empty();
        private OptionalInt looting = OptionalInt.empty();

        LootLevels(GameTestHelper helper, BlockPos ore) {
            this.helper = helper;
            this.ore = ore;
        }

        /**
         * Reads the fortune level the ore's loot rolls at.
         *
         * @param event the block loot event
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onBlockLoot(EnchantedBlockLootEvent event) {
            if (event.getPos().equals(ore) && event.getEnchantment().is(Enchantments.FORTUNE)) {
                fortune = OptionalInt.of(event.getEnchantmentLevel());
            }
        }

        /**
         * Reads the looting level a mob's loot in the test's level rolls at.
         *
         * @param event the entity loot event
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onEntityLoot(EnchantedEntityLootEvent event) {
            if (event.getEntity().level() == helper.getLevel() && event.getEnchantment().is(Enchantments.LOOTING)) {
                looting = OptionalInt.of(event.getEnchantmentLevel());
            }
        }
    }
}
