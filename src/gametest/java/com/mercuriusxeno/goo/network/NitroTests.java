package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Unstable's Nitro as a world goo through the real paths: its stream's
 * drips lay a layer of a Nitro cell each. Each test walls its cells in stone
 * so no cell pours its layers away.
 * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
 */
@RegistersGameTests
public final class NitroTests {

    private static final String DRIP_LAYS_A_CELL = "nitro_drip_lays_an_unstable_cell_of_one_layer";
    private static final Identifier NITRO = Identifier.parse("goo:unstable_nitro");
    /** The floor block the drip strikes, walled on its four sides one block up. */
    private static final BlockPos DRIP_FLOOR = new BlockPos(2, 1, 2);
    private static final String ABILITY_REQUIRED = "The ability registry must hold unstable_nitro";
    private static final String DRIP_LAYERS_WRONG = "The cell over the floor should be Nitro of 1 layer, stands %s";
    private static final String STEPPED_ON_SETS_OFF_THE_CHAIN = "nitro_stepped_on_sets_off_the_chain";
    /** The obsidian chamber's floor corner and its far upper corner. */
    private static final BlockPos CHAMBER_LOW = new BlockPos(0, 1, 0);
    private static final BlockPos CHAMBER_HIGH = new BlockPos(5, 4, 4);
    /** The cell the cow stands in, and the cell two blocks east of it the chain reaches across obsidian. */
    private static final BlockPos FIRST_CELL = new BlockPos(2, 2, 2);
    private static final BlockPos SECOND_CELL = new BlockPos(4, 2, 2);
    private static final String CELL_STANDS = "The Nitro cell at %s should be spent, stands %s";
    private static final String COW_UNHURT = "The cow should fall from %.1f health, reads %.1f";
    private static final String A_SIGNAL_SETS_IT_OFF = "nitro_a_redstone_signal_sets_it_off";
    /** The ticks the cell stands quiet before the redstone block takes the wall's place. */
    private static final int SIGNAL_AFTER = 5;
    private static final String MARKER_STANDS = "The dirt beside the Nitro cell should break in its blast";
    private static final String GOO_DROPPED = "Each Nitro cell should go off, not drop its goo: %s lie there";

    private NitroTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIP_LAYS_A_CELL, NitroTests::dripLaysAnUnstableCellOfOneLayer);
        GooTestFunctions.registerTest(registrar, STEPPED_ON_SETS_OFF_THE_CHAIN, NitroTests::steppedOnSetsOffTheChain);
        GooTestFunctions.registerTest(registrar, A_SIGNAL_SETS_IT_OFF, NitroTests::aRedstoneSignalSetsItOff);
    }

    /**
     * A full Nitro cell walled in obsidian on three sides and a dirt block
     * on the fourth goes off when a redstone block takes the place of one
     * obsidian wall: the cell is spent and its blast breaks the dirt.
     * nitro-explodes-when-touched-heated-shocked-pulsed-or-struck
     *
     * @param helper the gametest helper
     */
    public static void aRedstoneSignalSetsItOff(GameTestHelper helper) {
        BlockPos.betweenClosed(CHAMBER_LOW, CHAMBER_HIGH).forEach(pos -> helper.setBlock(pos,
                pos.getY() == CHAMBER_LOW.getY() ? Blocks.OBSIDIAN : Blocks.AIR));
        helper.setBlock(FIRST_CELL.north(), Blocks.OBSIDIAN);
        helper.setBlock(FIRST_CELL.south(), Blocks.OBSIDIAN);
        helper.setBlock(FIRST_CELL.west(), Blocks.OBSIDIAN);
        helper.setBlock(FIRST_CELL.east(), Blocks.DIRT);
        helper.setBlock(FIRST_CELL, GooBlocks.UNSTABLE_NITRO.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        helper.runAfterDelay(SIGNAL_AFTER, () -> helper.setBlock(FIRST_CELL.west(), Blocks.REDSTONE_BLOCK));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(FIRST_CELL).isAir(),
                    String.format(CELL_STANDS, FIRST_CELL, helper.getBlockState(FIRST_CELL)));
            helper.assertFalse(helper.getBlockState(FIRST_CELL.east()).is(Blocks.DIRT), MARKER_STANDS);
        });
    }

    /**
     * A cow standing in one full Nitro cell sets it off, and the blast sets
     * off a second full cell two blocks away across an obsidian block the
     * blast cannot break: both cells are spent and the cow is hurt. The
     * chamber is obsidian, so the blasts break nothing else.
     * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
     * nitro-explodes-when-touched-heated-shocked-pulsed-or-struck
     *
     * @param helper the gametest helper
     */
    public static void steppedOnSetsOffTheChain(GameTestHelper helper) {
        BlockPos.betweenClosed(CHAMBER_LOW, CHAMBER_HIGH).forEach(pos -> helper.setBlock(pos,
                pos.getY() == CHAMBER_LOW.getY() ? Blocks.OBSIDIAN : Blocks.AIR));
        for (BlockPos cell : new BlockPos[] {FIRST_CELL, SECOND_CELL}) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                helper.setBlock(cell.relative(side), Blocks.OBSIDIAN);
            }
            helper.setBlock(cell, GooBlocks.UNSTABLE_NITRO.get().defaultBlockState()
                    .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        }
        Mob cow = helper.spawnWithNoFreeWill(EntityTypes.COW, Vec3.atBottomCenterOf(FIRST_CELL));
        float start = cow.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(FIRST_CELL).isAir(),
                    String.format(CELL_STANDS, FIRST_CELL, helper.getBlockState(FIRST_CELL)));
            helper.assertTrue(helper.getBlockState(SECOND_CELL).isAir(),
                    String.format(CELL_STANDS, SECOND_CELL, helper.getBlockState(SECOND_CELL)));
            helper.assertTrue(cow.getHealth() < start, String.format(COW_UNHURT, start, cow.getHealth()));
            // a cell the blast broke rather than set off would lie there as the goo it held
            List<ItemEntity> goo = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(helper.absolutePos(CHAMBER_LOW)).minmax(new AABB(helper.absolutePos(CHAMBER_HIGH))),
                    item -> Goo.MODID.equals(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).getNamespace()));
            helper.assertTrue(goo.isEmpty(), String.format(GOO_DROPPED, goo));
        });
    }

    /**
     * One Nitro drip striking a walled stone top, from an unstable-holding
     * caster who pays for its layer, lays a Nitro cell of one layer.
     *
     * @param helper the gametest helper
     */
    public static void dripLaysAnUnstableCellOfOneLayer(GameTestHelper helper) {
        BlockPos cell = DRIP_FLOOR.above();
        helper.setBlock(DRIP_FLOOR, Blocks.STONE);
        helper.setBlock(cell, Blocks.AIR);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
        AbilityDefinition nitro = AbilityRegistry.of(helper.getLevel()).getAbility(NITRO);
        helper.assertTrue(nitro != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.UNSTABLE);
        KnownRecipes.teachRequires(caster, nitro);
        LandedDrips.landOn(helper, caster, nitro, DRIP_FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(cell);
        helper.assertTrue(state.is(GooBlocks.UNSTABLE_NITRO.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1,
                String.format(DRIP_LAYERS_WRONG, state));
        helper.succeed();
    }
}
