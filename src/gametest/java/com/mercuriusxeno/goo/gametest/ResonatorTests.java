package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Gametests for the resonator, a sound relay: a sculk sensor out of a sound's range and inside a resonator's hears the
 * sound through the resonator, as the same layout without one does not; and two resonators in each other's range
 * each echo a sound once and come to rest.
 * decision resonator-echoes-a-vibration-as-a-sound-relay
 */
@RegistersGameTests
public final class ResonatorTests {
    private static final String RESONATOR_RELAYS_A_SOUND_TO_A_SENSOR_OUT_OF_ITS_RANGE =
            "resonator_relays_a_sound_to_a_sensor_out_of_its_range";
    private static final String TWO_RESONATORS_EACH_ECHO_ONCE_AND_REST = "two_resonators_each_echo_once_and_rest";

    /**
     * Along march_bay's length: the relayed layout's sound, resonator and sensor six blocks apart, the sensor twelve
     * from the sound, past a sensor's eight; the bare layout's sound and sensor twelve apart, far down the bay.
     */
    private static final int FLOOR_Y = 1;
    private static final BlockPos RELAYED_SOUND = new BlockPos(1, FLOOR_Y, 3);
    private static final BlockPos RELAY = new BlockPos(7, FLOOR_Y, 3);
    private static final BlockPos RELAYED_SENSOR = new BlockPos(13, FLOOR_Y, 3);
    private static final BlockPos BARE_SOUND = new BlockPos(26, FLOOR_Y, 3);
    private static final BlockPos BARE_SENSOR = new BlockPos(38, FLOOR_Y, 3);
    /** Two resonators four apart, a sound between them. */
    private static final BlockPos FIRST = new BlockPos(1, FLOOR_Y, 3);
    private static final BlockPos SECOND = new BlockPos(5, FLOOR_Y, 3);
    private static final BlockPos BETWEEN = new BlockPos(3, FLOOR_Y, 3);
    private static final int WATCH_TICKS = 200;

    private static final String RELAYED_SENSOR_RESTS = "The sensor behind the resonator at %s should reach its active "
            + "phase, reads %s";
    private static final String BARE_SENSOR_HEARD = "The sensor with no resonator at %s should stay inactive, reads %s";
    private static final String ECHOED_OTHER_THAN_ONCE = "The resonator at %s should echo the sound once, echoed %d "
            + "times";
    private static final String RESONATOR_RESTLESS = "The resonator at %s should rest after its echo, reads %s";

    private ResonatorTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, RESONATOR_RELAYS_A_SOUND_TO_A_SENSOR_OUT_OF_ITS_RANGE,
                ResonatorTests::resonatorRelaysASoundToASensorOutOfItsRange);
        GooTestFunctions.registerTest(registrar, TWO_RESONATORS_EACH_ECHO_ONCE_AND_REST,
                ResonatorTests::twoResonatorsEachEchoOnceAndRest);
    }

    /**
     * Builds the relayed layout and the bare one, makes the same sound in each, and finds the sensor behind the
     * resonator active while the bare sensor stays inactive.
     *
     * @param helper the gametest helper
     */
    public static void resonatorRelaysASoundToASensorOutOfItsRange(GameTestHelper helper) {
        helper.setBlock(RELAY, GooBlocks.RESONATOR.get());
        helper.setBlock(RELAYED_SENSOR, Blocks.SCULK_SENSOR);
        helper.setBlock(BARE_SENSOR, Blocks.SCULK_SENSOR);
        helper.runAfterDelay(1, () -> {
            makeSound(helper, RELAYED_SOUND);
            makeSound(helper, BARE_SOUND);
        });
        helper.succeedWhen(() -> {
            BlockState bare = helper.getBlockState(BARE_SENSOR);
            helper.assertTrue(SculkSensorBlock.getPhase(bare) == SculkSensorPhase.INACTIVE,
                    String.format(BARE_SENSOR_HEARD, BARE_SENSOR, bare));
            BlockState relayed = helper.getBlockState(RELAYED_SENSOR);
            helper.assertTrue(SculkSensorBlock.getPhase(relayed) == SculkSensorPhase.ACTIVE,
                    String.format(RELAYED_SENSOR_RESTS, RELAYED_SENSOR, relayed));
        });
    }

    /**
     * Places two resonators in each other's range and makes one sound between them; over the next two hundred ticks
     * each echoes it exactly once, and both rest at the end.
     *
     * @param helper the gametest helper
     */
    public static void twoResonatorsEachEchoOnceAndRest(GameTestHelper helper) {
        helper.setBlock(FIRST, GooBlocks.RESONATOR.get());
        helper.setBlock(SECOND, GooBlocks.RESONATOR.get());
        BlockPos first = helper.absolutePos(FIRST);
        BlockPos second = helper.absolutePos(SECOND);
        Map<BlockPos, Integer> echoes = new HashMap<>();
        AtomicBoolean counting = new AtomicBoolean(true);
        NeoForge.EVENT_BUS.addListener((VanillaGameEvent event) -> {
            BlockPos at = BlockPos.containing(event.getEventPosition());
            BlockState source = event.getContext().affectedState();
            if (counting.get() && event.getLevel() == helper.getLevel() && source != null
                    && source.is(GooBlocks.RESONATOR.get()) && (at.equals(first) || at.equals(second))) {
                echoes.merge(at, 1, Integer::sum);
            }
        });
        helper.runAfterDelay(1, () -> makeSound(helper, BETWEEN));
        helper.runAfterDelay(WATCH_TICKS, () -> {
            counting.set(false);
            for (BlockPos resonator : new BlockPos[] {first, second}) {
                int count = echoes.getOrDefault(resonator, 0);
                helper.assertTrue(count == 1, String.format(ECHOED_OTHER_THAN_ONCE, resonator, count));
                BlockState state = helper.getLevel().getBlockState(resonator);
                helper.assertTrue(state.getValue(SculkSensorBlock.PHASE) == SculkSensorPhase.INACTIVE,
                        String.format(RESONATOR_RESTLESS, resonator, state));
            }
            helper.succeed();
        });
    }

    private static void makeSound(GameTestHelper helper, BlockPos at) {
        helper.getLevel().gameEvent(GameEvent.BLOCK_PLACE, Vec3.atCenterOf(helper.absolutePos(at)),
                GameEvent.Context.of(Blocks.STONE.defaultBlockState()));
    }
}
