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
 * pulse-goo-network-signals-at-its-highest-layer
 */
@RegistersGameTests
public final class SignalTests {

    private static final String SIGNAL_CELL_SENDS_ITS_LAYERS = "signal_cell_sends_its_layers";
    private static final String SIGNAL_LIGHTS_A_LAMP_BESIDE_IT = "signal_lights_a_lamp_beside_it";
    private static final String SIGNAL_DRIP_UNDER_A_CEILING_CLINGS_TO_IT = "signal_drip_under_a_ceiling_clings_to_it";
    /** A stone block hung in open air, three blocks over the floor, whose underside a drip strikes. */
    private static final BlockPos HUNG_BLOCK = new BlockPos(3, 4, 3);
    /** The open cell under the hung block, below its struck underside. */
    private static final BlockPos UNDER_THE_HUNG_BLOCK = HUNG_BLOCK.below();
    /** Ticks a laid cell is watched for a pour or a fall, past several of a cell's waits between pours. */
    private static final int SETTLING_TICKS = 60;
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
    private static final String SIGNAL_NETWORK_SIGNALS_AT_ITS_HIGHEST_LAYER = "signal_network_signals_at_its_highest_layer";
    private static final String SIGNAL_NETWORK_FOLLOWS_ITS_HIGHEST_LAYER = "signal_network_follows_its_highest_layer";
    /** The joined line's cells, west to east: its highest cell, its middle and its edge, each beside the next. */
    private static final BlockPos LINE_HIGHEST = new BlockPos(1, 1, 2);
    private static final BlockPos LINE_MIDDLE = LINE_HIGHEST.east();
    private static final BlockPos LINE_EDGE = LINE_MIDDLE.east();
    /** The lamp beside the line's edge cell, east of it. */
    private static final BlockPos LINE_LAMP = LINE_EDGE.east();
    private static final int LINE_MIDDLE_LAYERS = 3;
    private static final int LINE_EDGE_LAYERS = 1;
    /** The layers the line's highest cell is dropped to. */
    private static final int LOWERED_LAYERS = 6;
    private static final String NETWORK_SIGNAL_WRONG = "The Signal cell at %s should give its network's highest"
            + " layer count, %d, gives %d";
    private static final String LAMP_SIGNAL_WRONG = "The lamp beside the network's edge should read %d, reads %d";

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
        GooTestFunctions.registerTest(registrar, SIGNAL_DRIP_UNDER_A_CEILING_CLINGS_TO_IT,
                SignalTests::signalDripUnderACeilingClingsToIt);
        GooTestFunctions.registerTest(registrar, SIGNAL_NETWORK_SIGNALS_AT_ITS_HIGHEST_LAYER,
                SignalTests::signalNetworkSignalsAtItsHighestLayer);
        GooTestFunctions.registerTest(registrar, SIGNAL_NETWORK_FOLLOWS_ITS_HIGHEST_LAYER,
                SignalTests::signalNetworkFollowsItsHighestLayer);
    }

    /**
     * A line of three joined Signal cells of 15, 3 and 1 layers gives a 15
     * strength signal from every cell, the 1-layer cell at the far edge
     * included, and the redstone lamp beside that edge cell lights on it.
     * pulse-goo-network-signals-at-its-highest-layer
     *
     * @param helper the gametest helper
     */
    public static void signalNetworkSignalsAtItsHighestLayer(GameTestHelper helper) {
        layLine(helper);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    assertLineGives(helper, List.of(LINE_HIGHEST, LINE_MIDDLE, LINE_EDGE), VolumeCellBlock.MAX_LAYERS);
                    assertLampLitAt(helper, LINE_LAMP);
                    int lampReads = helper.getLevel().getBestNeighborSignal(helper.absolutePos(LINE_LAMP));
                    helper.assertTrue(lampReads == VolumeCellBlock.MAX_LAYERS,
                            String.format(LAMP_SIGNAL_WRONG, VolumeCellBlock.MAX_LAYERS, lampReads));
                })
                .thenSucceed();
    }

    /**
     * The line's 15-layer cell dropped to 6 makes every cell of the line
     * give 6 on the next tick; the middle cell removed splits the line, and
     * the 6-layer cell gives 6 while the 1-layer cell, alone, gives 1. The
     * checks run inside the pour's wait, before any cell pours a layer.
     * pulse-goo-network-signals-at-its-highest-layer
     *
     * @param helper the gametest helper
     */
    public static void signalNetworkFollowsItsHighestLayer(GameTestHelper helper) {
        layLine(helper);
        helper.startSequence()
                .thenExecuteAfter(1, () -> helper.setBlock(LINE_HIGHEST,
                        helper.getBlockState(LINE_HIGHEST).setValue(VolumeCellBlock.LAYERS, LOWERED_LAYERS)))
                .thenExecuteAfter(1, () -> {
                    assertLineGives(helper, List.of(LINE_HIGHEST, LINE_MIDDLE, LINE_EDGE), LOWERED_LAYERS);
                    helper.setBlock(LINE_MIDDLE, Blocks.AIR);
                    assertLineGives(helper, List.of(LINE_HIGHEST), LOWERED_LAYERS);
                    assertLineGives(helper, List.of(LINE_EDGE), LINE_EDGE_LAYERS);
                })
                .thenSucceed();
    }

    /**
     * Lays the line on a stone floor walled on every side but the lamp's:
     * a redstone lamp east of the edge, then the cells of 15, 3 and 1 layers
     * west to east, each set by its state.
     */
    private static void layLine(GameTestHelper helper) {
        for (int x = 0; x <= LINE_LAMP.getX() + 1; x++) {
            for (int z = LINE_HIGHEST.getZ() - 1; z <= LINE_HIGHEST.getZ() + 1; z++) {
                boolean lane = z == LINE_HIGHEST.getZ() && x > 0 && x <= LINE_LAMP.getX();
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), lane ? Blocks.AIR : Blocks.STONE);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
        }
        helper.setBlock(LINE_LAMP, Blocks.REDSTONE_LAMP);
        BlockState signal = GooBlocks.PULSE_SIGNAL.get().defaultBlockState();
        helper.setBlock(LINE_HIGHEST, signal.setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        helper.setBlock(LINE_MIDDLE, signal.setValue(VolumeCellBlock.LAYERS, LINE_MIDDLE_LAYERS));
        helper.setBlock(LINE_EDGE, signal.setValue(VolumeCellBlock.LAYERS, LINE_EDGE_LAYERS));
    }

    private static void assertLineGives(GameTestHelper helper, List<BlockPos> cells, int strength) {
        for (BlockPos cell : cells) {
            int gives = helper.getLevel().getSignal(helper.absolutePos(cell), Direction.EAST);
            helper.assertTrue(gives == strength, String.format(NETWORK_SIGNAL_WRONG, cell, strength, gives));
        }
    }

    /**
     * One Signal drip striking the floor of a sealed room lays a Signal cell
     * of one layer, and the stone wall beside it reads a redstone signal of 1
     * from its neighbours; fourteen more fill the cell, and the wall reads 15.
     * pulse-goo-network-signals-at-its-highest-layer
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
     * pulse-goo-network-signals-at-its-highest-layer
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
     * One Signal drip striking the underside of a stone block hung in open
     * air leaves a Signal cell under it, attached to that ceiling, and sixty
     * ticks on the cell still hangs there: pulse's adhesion holds an underside.
     * pulse-goo-network-signals-at-its-highest-layer
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
        assertLampLitAt(helper, BESIDE_THE_CELL);
    }

    private static void assertLampLitAt(GameTestHelper helper, BlockPos at) {
        BlockState lamp = helper.getBlockState(at);
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
