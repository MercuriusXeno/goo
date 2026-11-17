package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Leaf's Vines through the real landing its stream's drips take: a drip
 * striking stone's top lays a one-layer Vines cell there, and a husk walking
 * across a full Vines cell is held in place on it.
 * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
 * vines-root-is-volume-based-one-to-five-vines-by-layers
 */
@RegistersGameTests
public final class VinesTests {

    private static final String VINES_DRIP_LAYS_A_LEAF_CELL = "vines_drip_lays_a_leaf_cell_on_stone";
    private static final Identifier VINES = Identifier.parse("goo:leaf_vines");
    /** The floor block a drip strikes, in the middle of the bay. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    /** The cell over the floor block, where the drip's layer lands. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    private static final String ABILITY_REQUIRED = "The ability registry must hold leaf_vines";
    private static final String LAYERS_WRONG = "The cell over the stone should hold one layer of Vines, stands %s";
    private static final String FULL_CELL_HOLDS_A_WALKER = "vines_full_cell_holds_a_walker_in_place";
    /** The lane's westmost stone, where its walker starts; the lane runs east across the bay. */
    private static final BlockPos LANE = new BlockPos(0, 1, 2);
    /** How far east the lane runs from its start. */
    private static final int LANE_EAST = 5;
    /** The full Vines cell set into the lane, its top level with the stone beside it. */
    private static final BlockPos FULL_CELL = LANE.east(2);
    /** Two seconds, how long after reaching the cell the walker is read. */
    private static final int HELD_TICKS = 40;
    private static final String NOT_HELD = "The husk should stand over the Vines cell at x %d two seconds after "
            + "reaching it, stands at x %.3f";

    private VinesTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, VINES_DRIP_LAYS_A_LEAF_CELL, VinesTests::vinesDripLaysALeafCellOnStone);
        GooTestFunctions.registerTest(registrar, FULL_CELL_HOLDS_A_WALKER, VinesTests::vinesFullCellHoldsAWalkerInPlace);
    }

    /**
     * One Vines drip landing on stone's top lays a Vines cell of one layer over it.
     *
     * @param helper the gametest helper
     */
    public static void vinesDripLaysALeafCellOnStone(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(OVER_THE_FLOOR, Blocks.AIR);
        AbilityDefinition vines = AbilityRegistry.of(helper.getLevel()).getAbility(VINES);
        helper.assertTrue(vines != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.LEAF);
        KnownRecipes.teachRequires(caster, vines);
        LandedDrips.landOn(helper, caster, vines, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean oneLayer = BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(VINES)
                && state.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, state));
        helper.succeed();
    }

    /**
     * A husk walking east along a stone lane three blocks wide toward its
     * far end crosses a fifteen layer Vines cell set into the lane's middle, and two seconds after its
     * center first stands over the cell it still stands over it.
     *
     * @param helper the gametest helper
     */
    public static void vinesFullCellHoldsAWalkerInPlace(GameTestHelper helper) {
        // stone either side of the cell closes it in, so it pours none of its layers away
        for (int east = 0; east <= LANE_EAST; east++) {
            for (int across = -1; across <= 1; across++) {
                BlockPos stone = LANE.east(east).south(across);
                helper.setBlock(stone.below(), Blocks.BEDROCK);
                helper.setBlock(stone, Blocks.STONE);
                helper.setBlock(stone.above(), Blocks.AIR);
            }
        }
        helper.setBlock(FULL_CELL, GooBlocks.LEAF_VINES.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, LANE.above());
        helper.walkTo(husk, LANE.east(LANE_EAST).above(), 1f);
        int cellX = helper.absolutePos(FULL_CELL).getX();
        long[] reachedAt = {-1};
        helper.succeedWhen(() -> {
            boolean over = husk.getBlockX() == cellX;
            if (reachedAt[0] < 0 && over) {
                reachedAt[0] = helper.getTick();
            }
            helper.assertTrue(reachedAt[0] >= 0 && helper.getTick() - reachedAt[0] >= HELD_TICKS,
                    "The husk has not stood over the cell two seconds yet");
            helper.assertTrue(over, String.format(NOT_HELD, cellX, husk.getX()));
        });
    }
}
