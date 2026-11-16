package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.HostStep;
import com.mercuriusxeno.goo.block.ability.HostSpreaders;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Deep's Host through the real landing its stream's drips take: a drip
 * striking stone's top lays a one-layer Host cell there.
 * host-is-a-sculky-slime-that-sculk-spreads-eagerly-through
 */
@RegistersGameTests
public final class HostTests {

    private static final String HOST_DRIP_LAYS_A_CELL = "host_drip_lays_a_cell_on_stone";
    private static final Identifier HOST = Identifier.parse("goo:deep_host");
    /** The floor block a drip strikes, in the middle of the bay. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    /** The cell over the floor block, where the drip's layer lands. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    private static final String ABILITY_REQUIRED = "The ability registry must hold deep_host";
    private static final String LAYERS_WRONG = "The cell over the stone should hold one layer of Host, stands %s";
    private static final String CHARGE_TURNS_THE_STONE = "host_charge_turns_the_stone_under_it";
    private static final String DEATH_ON_HOST_SPREADS = "host_death_spreads_sculk_with_no_catalyst";
    private static final String STRANDED_CHARGE_ENDS = "host_stranded_charge_ends";
    private static final String CHARGE_NOT_STARTED = "The zombie's death on the Host cell should start one charge there";
    private static final String CHARGE_HELD = "A stranded charge should end, %s still run in the bay";
    private static final String NOT_SCULK = "The stone under the Host cell should be sculk, stands %s";
    private static final String LAYERS_LEFT_WRONG = "The Host cell should stand at %s layers, stands %s";

    private HostTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, HOST_DRIP_LAYS_A_CELL, HostTests::hostDripLaysACellOnStone);
        GooTestFunctions.registerTest(registrar, CHARGE_TURNS_THE_STONE, HostTests::chargeCrossingHostTurnsTheStoneUnderIt);
        GooTestFunctions.registerTest(registrar, DEATH_ON_HOST_SPREADS, HostTests::deathOnHostSpreadsSculkWithNoCatalyst);
        GooTestFunctions.registerTest(registrar, STRANDED_CHARGE_ENDS, HostTests::strandedChargeEnds);
    }

    /**
     * A charge started on a lone one-layer Host cell walled in stone, too
     * thin to turn the stone under it and with no step off the goo, ends
     * rather than holding its charge for as long as the server runs.
     * sculk-charge-runs-along-host-goo-without-weakening
     * mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo
     *
     * @param helper the gametest helper
     */
    public static void strandedChargeEnds(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(OVER_THE_FLOOR.relative(side), Blocks.STONE);
        }
        helper.setBlock(OVER_THE_FLOOR, GooBlocks.DEEP_HOST.get().defaultBlockState());
        ServerPlayer killer = SelfDeliveryTests.invoker(helper, GooTypes.DEEP);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, OVER_THE_FLOOR);
        zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(killer), Float.MAX_VALUE);
        helper.getLevel().getServer().getPlayerList().remove(killer);
        HostSpreaders spreaders = GooServerState.of(helper.getLevel().getServer()).hostSpreaders();
        AABB bay = helper.getBounds();
        helper.assertTrue(spreaders.chargesWithin(helper.getLevel(), bay) == 1, CHARGE_NOT_STARTED);
        helper.succeedWhen(() -> helper.assertTrue(spreaders.chargesWithin(helper.getLevel(), bay) == 0,
                String.format(CHARGE_HELD, spreaders.chargesWithin(helper.getLevel(), bay))));
    }

    /**
     * A zombie a player kills on a walled full Host cell, with no catalyst in
     * the bay, turns the stone under the cell to sculk and drops no
     * experience, the goo taking it as a catalyst would.
     * mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo
     *
     * @param helper the gametest helper
     */
    public static void deathOnHostSpreadsSculkWithNoCatalyst(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(OVER_THE_FLOOR.relative(side), Blocks.STONE);
        }
        helper.setBlock(OVER_THE_FLOOR, GooBlocks.DEEP_HOST.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        ServerPlayer killer = SelfDeliveryTests.invoker(helper, GooTypes.DEEP);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, OVER_THE_FLOOR.above());
        zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(killer), Float.MAX_VALUE);
        helper.getLevel().getServer().getPlayerList().remove(killer);
        helper.assertTrue(zombie.isDeadOrDying(), "The zombie should die of the blow");
        helper.succeedWhen(() -> {
            BlockState under = helper.getBlockState(FLOOR);
            helper.assertTrue(under.is(Blocks.SCULK), String.format(NOT_SCULK, under));
            helper.assertEntityNotPresent(EntityType.EXPERIENCE_ORB);
        });
    }

    /**
     * A sculk charge from a catalyst crossing a full Host cell turns the stone
     * under the cell to sculk, and the cell stands lower by the layers Host's
     * step names a block of sculk spends. The cell stands walled on three
     * sides and against the catalyst on the fourth, so it cannot pour; a
     * zombie killed on top of it starts the catalyst's charge there.
     * sculk-charge-runs-along-host-goo-without-weakening
     *
     * @param helper the gametest helper
     */
    public static void chargeCrossingHostTurnsTheStoneUnderIt(GameTestHelper helper) {
        AbilityDefinition host = AbilityRegistry.of(helper.getLevel()).getAbility(HOST);
        helper.assertTrue(host != null, ABILITY_REQUIRED);
        int sculkLayers = HostStep.in(host.behaviors()).orElseThrow().sculkLayers();
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(FLOOR.west(), Blocks.STONE);
        helper.setBlock(OVER_THE_FLOOR.west(), Blocks.SCULK_CATALYST);
        for (Direction side : List.of(Direction.EAST, Direction.NORTH, Direction.SOUTH)) {
            helper.setBlock(OVER_THE_FLOOR.relative(side), Blocks.STONE);
        }
        helper.setBlock(OVER_THE_FLOOR, GooBlocks.DEEP_HOST.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, OVER_THE_FLOOR.above());
        zombie.kill(helper.getLevel());
        helper.succeedWhen(() -> {
            BlockState under = helper.getBlockState(FLOOR);
            helper.assertTrue(under.is(Blocks.SCULK), String.format(NOT_SCULK, under));
            BlockState cell = helper.getBlockState(OVER_THE_FLOOR);
            boolean spent = cell.is(GooBlocks.DEEP_HOST.get())
                    && cell.getValue(VolumeCellBlock.LAYERS) == VolumeCellBlock.MAX_LAYERS - sculkLayers;
            helper.assertTrue(spent, String.format(LAYERS_LEFT_WRONG, VolumeCellBlock.MAX_LAYERS - sculkLayers, cell));
        });
    }

    /**
     * One Host drip striking a stone block's top, from a deep-holding caster
     * who pays for its layer, lays a Host cell of one layer over it.
     *
     * @param helper the gametest helper
     */
    public static void hostDripLaysACellOnStone(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(OVER_THE_FLOOR, Blocks.AIR);
        AbilityDefinition host = AbilityRegistry.of(helper.getLevel()).getAbility(HOST);
        helper.assertTrue(host != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.DEEP);
        KnownRecipes.teachRequires(caster, host);
        LandedDrips.landOn(helper, caster, host, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean oneLayer = state.is(GooBlocks.DEEP_HOST.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, state));
        helper.succeed();
    }
}
