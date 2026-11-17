package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Jelly's Nectar, the jelly goo its held stream sprays: a drip
 * striking a block's top, from a jelly-holding caster who pays for its
 * layer, lays a Nectar cell of one layer there.
 * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
 */
@RegistersGameTests
public final class NectarTests {

    private static final String DRIP_LAYS_A_JELLY_CELL = "nectar_drip_lays_a_jelly_cell_of_one_layer";
    private static final Identifier NECTAR = Identifier.parse("goo:jelly_nectar");
    /** The floor block the drip strikes, walled on its four sides one block up. */
    private static final BlockPos DRIP_FLOOR = new BlockPos(2, 1, 2);
    private static final int ONE_DRIP = 1;
    private static final String ABILITY_REQUIRED = "The ability registry must hold jelly_nectar";
    private static final String DRIP_LAYERS_WRONG = "The cell over the floor should be Nectar of one layer, stands %s";

    private NectarTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIP_LAYS_A_JELLY_CELL, NectarTests::dripLaysAJellyCellOfOneLayer);
    }

    /**
     * One Nectar drip striking a walled stone floor, from a jelly-holding
     * caster who pays for its layer, lays a Nectar cell of one layer.
     *
     * @param helper the gametest helper
     */
    public static void dripLaysAJellyCellOfOneLayer(GameTestHelper helper) {
        BlockPos cell = DRIP_FLOOR.above();
        helper.setBlock(DRIP_FLOOR, Blocks.STONE);
        helper.setBlock(cell, Blocks.AIR);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
        AbilityDefinition nectar = AbilityRegistry.of(helper.getLevel()).getAbility(NECTAR);
        helper.assertTrue(nectar != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(caster, nectar);
        LandedDrips.landOn(helper, caster, nectar, DRIP_FLOOR, ONE_DRIP);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(cell);
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(NECTAR)
                && state.getValue(VolumeCellBlock.LAYERS) == ONE_DRIP, String.format(DRIP_LAYERS_WRONG, state));
        helper.succeed();
    }
}
