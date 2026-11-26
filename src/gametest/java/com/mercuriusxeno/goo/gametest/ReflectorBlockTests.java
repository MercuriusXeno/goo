package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.ability.LightRailBlock;
import com.mercuriusxeno.goo.block.alientech.ReflectorBlock;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for the reflector block: it registers and drops itself unrestricted from melting; two reflectors with
 * air between them, open lens to open lens, join by a light rail that lights its cells and lets a block be placed in
 * them; a solid block between two reflectors leaves them unjoined; and the closed lens on the side a reflector
 * attaches by joins nothing.
 * decision reflector-rails-carry-the-brightest-light
 * decision utility-blocks-found-in-lunar-are-learned-and-recreated-at-will
 */
@RegistersGameTests
public final class ReflectorBlockTests {
    private static final String REFLECTOR_REGISTERS_AND_DROPS = "reflector_registers_and_drops";
    private static final String REFLECTORS_JOIN_BY_A_LIT_RAIL = "reflectors_join_by_a_lit_rail";
    private static final String SOLID_BLOCK_ENDS_THE_RAIL = "reflector_solid_block_ends_the_rail";
    private static final String CLOSED_LENS_JOINS_NOTHING = "reflector_closed_lens_joins_nothing";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, REFLECTOR_REGISTERS_AND_DROPS, ReflectorBlockTests::registersAndDrops);
        GooTestFunctions.registerTest(registrar, REFLECTORS_JOIN_BY_A_LIT_RAIL, ReflectorBlockTests::joinByALitRail);
        GooTestFunctions.registerTest(registrar, SOLID_BLOCK_ENDS_THE_RAIL, ReflectorBlockTests::solidBlockEndsTheRail);
        GooTestFunctions.registerTest(registrar, CLOSED_LENS_JOINS_NOTHING, ReflectorBlockTests::closedLensJoinsNothing);
    }

    private static final BlockPos NEAR = new BlockPos(0, 1, 2);
    private static final BlockPos FAR = new BlockPos(5, 1, 2);
    private static final BlockPos MIDDLE = new BlockPos(2, 1, 2);
    /** The closed-lens column: a reflector on a raised support, a second on the floor below it. */
    private static final BlockPos RAISED = new BlockPos(2, 3, 2);
    private static final BlockPos BELOW_RAISED = RAISED.below();
    private static final BlockPos FLOOR_REFLECTOR = BELOW_RAISED.below();
    private static final double DROP_REACH = 2.0;
    /** Ticks watched: past a reflector's first refresh and its next, a second apart. */
    private static final int WATCH_TICKS = 45;

    private static final String NOT_REGISTERED = "goo:reflector should be a ReflectorBlock with its BlockItem";
    private static final String WRONG_DROP = "A broken reflector should drop one reflector, but dropped %s";
    private static final String RESTRICTED = "goo:reflector should be neither restricted nor denied from melting";
    private static final String NO_RAIL = "%s between the reflectors should be light rail, it is %s";
    private static final String UNLIT = "A rail cell should light to its rail's level %d, it reads %d";
    private static final String NOT_REPLACEABLE = "A rail cell should take a placed block";
    private static final String RAIL_ACROSS = "No rail should join reflectors with stone between, %s is rail";
    /** A rail laid through the closed lens knocks the raised reflector off its emptied support, so either shows it. */
    private static final String CLOSED_LENS_RAIL = "No rail should leave the lens a reflector attaches by: on tick %d "
            + "the raised cell held %s and the cell below it %s";

    private ReflectorBlockTests() {
    }

    /**
     * The registries hold goo:reflector and its BlockItem, a broken reflector drops one reflector, and the live goo
     * value table reads it neither restricted nor denied.
     *
     * @param helper the gametest helper
     */
    public static void registersAndDrops(GameTestHelper helper) {
        Block block = BuiltInRegistries.BLOCK.getValue(GooBlocks.REFLECTOR.getId());
        helper.assertTrue(block instanceof ReflectorBlock
                && BuiltInRegistries.ITEM.getValue(GooItems.REFLECTOR.getId()) instanceof BlockItem item
                && item.getBlock() == block, NOT_REGISTERED);
        standOnFloor(helper, NEAR);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(NEAR);
        Block.dropResources(level.getBlockState(pos), level, pos, level.getBlockEntity(pos), null, ItemStack.EMPTY);
        List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, NEAR, DROP_REACH);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(GooItems.REFLECTOR.get())
                && drops.getFirst().getItem().getCount() == 1,
                String.format(WRONG_DROP, drops.stream().map(ItemEntity::getItem).toList()));
        drops.forEach(ItemEntity::discard);
        IGooValueLookup values = GooValues.of(level);
        helper.assertFalse(values.isRestricted(GooItems.REFLECTOR.getId())
                || values.isDenied(GooItems.REFLECTOR.getId()), RESTRICTED);
        helper.succeed();
    }

    /**
     * Two floor reflectors with air between them join by rail: every cell between is light rail, lit to its level,
     * and replaceable as air is.
     *
     * @param helper the gametest helper
     */
    public static void joinByALitRail(GameTestHelper helper) {
        standOnFloor(helper, NEAR);
        standOnFloor(helper, FAR);
        helper.succeedWhen(() -> {
            for (int x = NEAR.getX() + 1; x < FAR.getX(); x++) {
                BlockPos cell = new BlockPos(x, NEAR.getY(), NEAR.getZ());
                helper.assertTrue(helper.getBlockState(cell).is(GooBlocks.LIGHT_RAIL.get()),
                        String.format(NO_RAIL, cell, helper.getBlockState(cell)));
            }
            BlockState rail = helper.getBlockState(MIDDLE);
            int level = rail.getValue(LightRailBlock.LEVEL);
            int light = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(MIDDLE));
            helper.assertTrue(light == level && light > 0, String.format(UNLIT, level, light));
            helper.assertTrue(rail.canBeReplaced(), NOT_REPLACEABLE);
        });
    }

    /**
     * Two floor reflectors with stone between them stay unjoined: no cell between holds rail.
     *
     * @param helper the gametest helper
     */
    public static void solidBlockEndsTheRail(GameTestHelper helper) {
        helper.setBlock(MIDDLE, Blocks.STONE);
        standOnFloor(helper, NEAR);
        standOnFloor(helper, FAR);
        helper.runAfterDelay(WATCH_TICKS, () -> {
            for (int x = NEAR.getX() + 1; x < FAR.getX(); x++) {
                BlockPos cell = new BlockPos(x, NEAR.getY(), NEAR.getZ());
                helper.assertFalse(helper.getBlockState(cell).is(GooBlocks.LIGHT_RAIL.get()),
                        String.format(RAIL_ACROSS, cell));
            }
            helper.succeed();
        });
    }

    /**
     * A reflector standing on a raised support, with a second reflector on the floor below the support, joins
     * nothing through the closed lens it attaches by. The support is taken away without telling its neighbors, so
     * only air lies between the two and the lens alone keeps them apart; the cell between is watched every tick,
     * since a rail laid there would tell the raised reflector it lost its support and both would go.
     *
     * @param helper the gametest helper
     */
    public static void closedLensJoinsNothing(GameTestHelper helper) {
        helper.setBlock(BELOW_RAISED, Blocks.STONE);
        helper.setBlock(RAISED, reflectorFacing(Direction.UP));
        standOnFloor(helper, FLOOR_REFLECTOR);
        helper.getLevel().setBlock(helper.absolutePos(BELOW_RAISED), Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        for (int tick = 1; tick <= WATCH_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> helper.assertTrue(
                    helper.getBlockState(RAISED).is(GooBlocks.REFLECTOR.get())
                            && !helper.getBlockState(BELOW_RAISED).is(GooBlocks.LIGHT_RAIL.get()),
                    String.format(CLOSED_LENS_RAIL, at, helper.getBlockState(RAISED),
                            helper.getBlockState(BELOW_RAISED))));
        }
        helper.runAfterDelay(WATCH_TICKS + 1, helper::succeed);
    }

    /** Stands a reflector on stone, pointing up, its closed lens on the floor. */
    private static void standOnFloor(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, reflectorFacing(Direction.UP));
    }

    private static BlockState reflectorFacing(Direction facing) {
        return GooBlocks.REFLECTOR.get().defaultBlockState().setValue(ReflectorBlock.FACING, facing);
    }
}
