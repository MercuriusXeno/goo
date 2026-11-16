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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Glow's Wisps through the real landing its stream's drips take, in a
 * sealed dark stone room: a drip striking the floor lays a one-layer Wisps
 * cell shining at light 1, and a full cell shines at 15.
 * glow-world-goo-is-named-wisps-and-takes-the-holds-place
 * wisps-goo-glows-at-a-light-level-equal-to-its-layers
 */
@RegistersGameTests
public final class WispsTests {

    private static final String WISPS_CELL_SHINES_AT_ITS_LAYERS = "wisps_cell_shines_at_its_layers";
    private static final Identifier WISPS = Identifier.parse("goo:glow_wisps");
    /** The room spans 0 to 4 across and 0 to 4 up, stone but for the shaft over the floor block. */
    private static final int ROOM_EDGE = 4;
    /** The floor block the drips strike, in the middle of the room. */
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    /** The cell over the floor block, where the drips' layers land. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    /** Ticks the light engine takes to light the room after the cell changes. */
    private static final int LIGHT_SETTLE_TICKS = 4;
    private static final String ABILITY_REQUIRED = "The ability registry must hold glow_wisps";
    private static final String CELL_WRONG = "The cell over the floor should hold %d layers of Wisps, stands %s";
    private static final String LIGHT_WRONG = "A Wisps cell of %d layers should shine at block light %d, reads %d";

    private WispsTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, WISPS_CELL_SHINES_AT_ITS_LAYERS,
                WispsTests::wispsCellShinesAtItsLayers);
    }

    /**
     * One Wisps drip striking the floor of a sealed dark room lays a Wisps
     * cell of one layer shining at block light 1; fourteen more fill the
     * cell, and it shines at 15.
     *
     * @param helper the gametest helper
     */
    public static void wispsCellShinesAtItsLayers(GameTestHelper helper) {
        sealRoom(helper);
        AbilityDefinition wisps = AbilityRegistry.of(helper.getLevel()).getAbility(WISPS);
        helper.assertTrue(wisps != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.GLOW);
        KnownRecipes.teachRequires(caster, wisps);
        LandedDrips.landOn(helper, caster, wisps, FLOOR, 1);
        helper.runAfterDelay(LIGHT_SETTLE_TICKS, () -> {
            assertShinesAt(helper, 1);
            LandedDrips.landOn(helper, caster, wisps, FLOOR, VolumeCellBlock.MAX_LAYERS - 1);
            helper.runAfterDelay(LIGHT_SETTLE_TICKS, () -> {
                helper.getLevel().getServer().getPlayerList().remove(caster);
                assertShinesAt(helper, VolumeCellBlock.MAX_LAYERS);
                helper.succeed();
            });
        });
    }

    private static void assertShinesAt(GameTestHelper helper, int layers) {
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean held = state.is(GooBlocks.GLOW_WISPS.get()) && state.getValue(VolumeCellBlock.LAYERS) == layers;
        helper.assertTrue(held, String.format(CELL_WRONG, layers, state));
        int light = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(OVER_THE_FLOOR));
        helper.assertTrue(light == layers, String.format(LIGHT_WRONG, layers, layers, light));
    }

    /**
     * Seals a stone room whose only air is the one-block shaft over the
     * floor block, so the goo has no neighbour to pour into and its layers
     * stack in the cell.
     */
    private static void sealRoom(GameTestHelper helper) {
        for (int x = 0; x <= ROOM_EDGE; x++) {
            for (int y = 0; y <= ROOM_EDGE; y++) {
                for (int z = 0; z <= ROOM_EDGE; z++) {
                    boolean shaft = x == FLOOR.getX() && z == FLOOR.getZ() && y > FLOOR.getY() && y < ROOM_EDGE;
                    helper.setBlock(new BlockPos(x, y, z), shaft ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
    }
}
