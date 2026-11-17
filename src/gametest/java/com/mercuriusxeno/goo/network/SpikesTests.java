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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Metal's Spikes through the real landing its stream's drips take: a drip
 * striking stone's top lays a one-layer Spikes cell there.
 * metal-world-goo-is-named-spikes
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 */
@RegistersGameTests
public final class SpikesTests {

    private static final String SPIKES_DRIP_LAYS_A_CELL = "spikes_drip_lays_a_metal_cell_of_one_layer";
    private static final Identifier SPIKES = Identifier.parse("goo:metal_spikes");
    /** The stone the drip strikes, in the middle of the bay; the cell stands over it. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    private static final String ABILITY_REQUIRED = "The ability registry must hold metal_spikes";
    private static final String LAYERS_WRONG = "The cell over the stone should hold one layer of Spikes, stands %s";

    private SpikesTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SPIKES_DRIP_LAYS_A_CELL, SpikesTests::spikesDripLaysAMetalCellOfOneLayer);
    }

    /**
     * One Spikes drip striking a stone's top lays a Spikes cell of one layer over it.
     *
     * @param helper the gametest helper
     */
    public static void spikesDripLaysAMetalCellOfOneLayer(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE.defaultBlockState());
        AbilityDefinition spikes = AbilityRegistry.of(helper.getLevel()).getAbility(SPIKES);
        helper.assertTrue(spikes != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.METAL);
        KnownRecipes.teachRequires(caster, spikes);
        LandedDrips.landOn(helper, caster, spikes, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState cell = helper.getBlockState(FLOOR.above());
        boolean oneLayer = cell.is(GooBlocks.METAL_SPIKES.get()) && cell.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, cell));
        helper.succeed();
    }
}
