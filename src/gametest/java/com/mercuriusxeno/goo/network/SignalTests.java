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
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pulse's Signal through the real landing its stream's drips take, in a
 * sealed stone room: a drip striking the floor lays a one-layer Signal cell
 * the stone wall beside it reads a redstone signal of 1 from, fourteen more
 * fill it to 15, and a redstone lamp set in the wall beside the cell lights
 * when the cell is laid and stays lit while the cell stands.
 * extend-is-dropped-for-signal-an-in-world-pulse-goo
 * signal-goo-sends-a-redstone-signal-equal-to-its-layers
 */
@RegistersGameTests
public final class SignalTests {

    private static final String SIGNAL_CELL_SENDS_ITS_LAYERS = "signal_cell_sends_its_layers";
    private static final String SIGNAL_LIGHTS_A_LAMP_BESIDE_IT = "signal_lights_a_lamp_beside_it";
    private static final String SIGNAL_HOLDS_ITS_LAYERS_ON_OPEN_GROUND = "signal_holds_its_layers_on_open_ground";
    private static final String SIGNAL_DRIP_UNDER_A_CEILING_CLINGS_TO_IT = "signal_drip_under_a_ceiling_clings_to_it";
    /** The open ground's middle cell, where every drip of the open ground test lands. */
    private static final BlockPos OPEN_CELL = new BlockPos(3, 1, 3);
    /** A stone block hung in open air, three blocks over the floor, whose underside a drip strikes. */
    private static final BlockPos HUNG_BLOCK = new BlockPos(3, 4, 3);
    /** The open cell under the hung block, below its struck underside. */
    private static final BlockPos UNDER_THE_HUNG_BLOCK = HUNG_BLOCK.below();
    /** Ticks a laid cell is watched for a pour or a fall, past several of a cell's waits between pours. */
    private static final int SETTLING_TICKS = 60;
    private static final String SPREAD = "Signal on open ground should keep all %d layers in its cell, the cross reads %s";
    private static final String NOT_ON_THE_CEILING = "A Signal drip striking a block's underside should leave a cell"
            + " attached to that ceiling, stands %s";
    private static final Identifier SIGNAL = Identifier.parse("goo:pulse_signal");
    /** The room spans 0 to 4 across and 0 to 4 up, stone but for the shaft over the floor block. */
    private static final int ROOM_EDGE = 4;
    /** The floor block the drips strike, in the middle of the room. */
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    /** The cell over the floor block, where the drips' layers land. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    /** The wall block beside the cell, which reads the signal or holds the lamp. */
    private static final BlockPos BESIDE_THE_CELL = OVER_THE_FLOOR.west();
    /** Ticks the lamp is watched for after it lights. */
    private static final int STANDING_TICKS = 40;
    /** The layers the lamp test lays. */
    private static final int LAMP_LAYERS = 6;
    private static final String ABILITY_REQUIRED = "The ability registry must hold pulse_signal";
    private static final String CELL_WRONG = "The cell over the floor should hold %d layers of Signal, stands %s";
    private static final String SIGNAL_WRONG = "A Signal cell of %d layers should send a signal of %d, the wall reads %d";
    private static final String LAMP_UNLIT = "The lamp beside a Signal cell should be lit, stands %s";

    private SignalTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SIGNAL_CELL_SENDS_ITS_LAYERS, SignalTests::signalCellSendsItsLayers);
        GooTestFunctions.registerTest(registrar, SIGNAL_LIGHTS_A_LAMP_BESIDE_IT,
                SignalTests::signalLightsALampBesideIt);
        GooTestFunctions.registerTest(registrar, SIGNAL_HOLDS_ITS_LAYERS_ON_OPEN_GROUND,
                SignalTests::signalHoldsItsLayersOnOpenGround);
        GooTestFunctions.registerTest(registrar, SIGNAL_DRIP_UNDER_A_CEILING_CLINGS_TO_IT,
                SignalTests::signalDripUnderACeilingClingsToIt);
    }

    /**
     * One Signal drip striking the floor of a sealed room lays a Signal cell
     * of one layer, and the stone wall beside it reads a redstone signal of 1
     * from its neighbours; fourteen more fill the cell, and the wall reads 15.
     * signal-goo-sends-a-redstone-signal-equal-to-its-layers
     *
     * @param helper the gametest helper
     */
    public static void signalCellSendsItsLayers(GameTestHelper helper) {
        sealRoom(helper, Blocks.STONE.defaultBlockState());
        AbilityDefinition signal = signalAbility(helper);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.PULSE);
        KnownRecipes.teachRequires(caster, signal);
        LandedDrips.landOn(helper, caster, signal, FLOOR, 1);
        helper.startSequence()
                .thenWaitUntil(() -> assertSendsAt(helper, 1))
                .thenExecute(() -> LandedDrips.landOn(helper, caster, signal, FLOOR, VolumeCellBlock.MAX_LAYERS - 1))
                .thenWaitUntil(() -> assertSendsAt(helper, VolumeCellBlock.MAX_LAYERS))
                .thenExecute(() -> helper.getLevel().getServer().getPlayerList().remove(caster))
                .thenSucceed();
    }

    /**
     * A redstone lamp set in the wall beside the floor's cell lights once
     * Signal drips lay a cell there, and forty ticks on the lamp is still
     * lit and the cell still holds the layers it was laid with.
     * signal-goo-sends-a-redstone-signal-equal-to-its-layers
     *
     * @param helper the gametest helper
     */
    public static void signalLightsALampBesideIt(GameTestHelper helper) {
        sealRoom(helper, Blocks.REDSTONE_LAMP.defaultBlockState());
        AbilityDefinition signal = signalAbility(helper);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.PULSE);
        KnownRecipes.teachRequires(caster, signal);
        LandedDrips.landOn(helper, caster, signal, FLOOR, LAMP_LAYERS);
        helper.startSequence()
                .thenWaitUntil(() -> assertLampLit(helper))
                .thenIdle(STANDING_TICKS)
                .thenExecute(() -> {
                    assertLampLit(helper);
                    assertHolds(helper, LAMP_LAYERS);
                })
                .thenExecute(() -> helper.getLevel().getServer().getPlayerList().remove(caster))
                .thenSucceed();
    }

    /**
     * Fifteen Signal drips landing on one cell of open stone ground fill it,
     * and sixty ticks on the cell still holds all fifteen and its four side
     * cells hold none: pulse's goo is firm, so the layers a player lays are
     * the signal the cell sends.
     * signal-goo-sends-a-redstone-signal-equal-to-its-layers
     * cohesion-is-the-step-the-goo-holds
     *
     * @param helper the gametest helper
     */
    public static void signalHoldsItsLayersOnOpenGround(GameTestHelper helper) {
        OpenFloor.layAround(helper, OPEN_CELL);
        AbilityDefinition signal = signalAbility(helper);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.PULSE);
        KnownRecipes.teachRequires(caster, signal);
        LandedDrips.landOn(helper, caster, signal, OPEN_CELL, VolumeCellBlock.MAX_LAYERS);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        List<Integer> heldWhole = List.of(VolumeCellBlock.MAX_LAYERS, 0, 0, 0, 0);
        helper.runAfterDelay(SETTLING_TICKS, () -> {
            List<Integer> cross = OpenFloor.layersIn(helper, OpenFloor.crossAt(OPEN_CELL));
            helper.assertTrue(heldWhole.equals(cross), String.format(SPREAD, VolumeCellBlock.MAX_LAYERS, cross));
            helper.succeed();
        });
    }

    /**
     * One Signal drip striking the underside of a stone block hung in open
     * air leaves a Signal cell under it, attached to that ceiling, and sixty
     * ticks on the cell still hangs there: pulse's adhesion holds an underside.
     * signal-goo-sends-a-redstone-signal-equal-to-its-layers
     * adhesion-is-a-ladder-of-holds
     *
     * @param helper the gametest helper
     */
    public static void signalDripUnderACeilingClingsToIt(GameTestHelper helper) {
        helper.setBlock(HUNG_BLOCK, Blocks.STONE);
        for (int y = 1; y < HUNG_BLOCK.getY(); y++) {
            helper.setBlock(new BlockPos(HUNG_BLOCK.getX(), y, HUNG_BLOCK.getZ()), Blocks.AIR);
        }
        AbilityDefinition signal = signalAbility(helper);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.PULSE);
        KnownRecipes.teachRequires(caster, signal);
        LandedDrips.landOnSide(helper, caster, signal, HUNG_BLOCK, Direction.DOWN, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.runAfterDelay(SETTLING_TICKS, () -> {
            BlockState cell = helper.getBlockState(UNDER_THE_HUNG_BLOCK);
            boolean clinging = cell.is(GooBlocks.PULSE_SIGNAL.get()) && cell.getValue(VolumeCellBlock.CEILING);
            helper.assertTrue(clinging, String.format(NOT_ON_THE_CEILING, cell));
            helper.succeed();
        });
    }

    private static AbilityDefinition signalAbility(GameTestHelper helper) {
        AbilityDefinition signal = AbilityRegistry.of(helper.getLevel()).getAbility(SIGNAL);
        helper.assertTrue(signal != null, ABILITY_REQUIRED);
        return signal;
    }

    private static void assertSendsAt(GameTestHelper helper, int layers) {
        assertHolds(helper, layers);
        int signal = helper.getLevel().getBestNeighborSignal(helper.absolutePos(BESIDE_THE_CELL));
        helper.assertTrue(signal == layers, String.format(SIGNAL_WRONG, layers, layers, signal));
    }

    private static void assertHolds(GameTestHelper helper, int layers) {
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean held = state.is(GooBlocks.PULSE_SIGNAL.get()) && state.getValue(VolumeCellBlock.LAYERS) == layers;
        helper.assertTrue(held, String.format(CELL_WRONG, layers, state));
    }

    private static void assertLampLit(GameTestHelper helper) {
        BlockState lamp = helper.getBlockState(BESIDE_THE_CELL);
        helper.assertTrue(lamp.is(Blocks.REDSTONE_LAMP) && lamp.getValue(RedstoneLampBlock.LIT),
                String.format(LAMP_UNLIT, lamp));
    }

    /**
     * Seals a stone room whose only air is the one-block shaft over the
     * floor block, so the goo has no neighbour to pour into and its layers
     * stack in the cell, with the wall block beside the cell set apart.
     */
    private static void sealRoom(GameTestHelper helper, BlockState besideTheCell) {
        for (int x = 0; x <= ROOM_EDGE; x++) {
            for (int y = 0; y <= ROOM_EDGE; y++) {
                for (int z = 0; z <= ROOM_EDGE; z++) {
                    boolean shaft = x == FLOOR.getX() && z == FLOOR.getZ() && y > FLOOR.getY() && y < ROOM_EDGE;
                    helper.setBlock(new BlockPos(x, y, z), shaft ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
        helper.setBlock(BESIDE_THE_CELL, besideTheCell);
    }
}
