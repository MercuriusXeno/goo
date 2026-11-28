package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.pulse.ZapDevice;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarDepths;
import com.mercuriusxeno.goo.lunar.WarrenMechanism;
import com.mercuriusxeno.goo.lunar.WirelessBeam;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametests for the warrens' armed sculk: the generated warrens hold a sculk sensor wired through a wireless emitter to
 * a volatile crystal, beside sensors wired to nothing; and a vibration beside a mechanism's sensor, or one out of its
 * range inside its resonator's, sets its crystal off.
 * decision sculk-sensors-wire-to-pulse-emitter-crystals-that-set-off-volatile-crystals
 */
@RegistersGameTests
public final class WarrenMechanismTests {
    private static final String LUNAR_WARRENS_WIRE_SENSORS_TO_VOLATILE_CRYSTALS =
            "lunar_warrens_wire_sensors_to_volatile_crystals";
    private static final String A_VIBRATION_SETS_OFF_A_MECHANISMS_CRYSTAL = "a_vibration_sets_off_a_mechanisms_crystal";

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_MECHANISM = "The warrens should hold a sculk sensor touching a wireless emitter whose "
            + "closest receiver within 16 is a volatile crystal, finds %d sensors, %d touching an emitter";
    private static final String NO_BARE_SENSOR = "The warrens should hold a sculk sensor touching no emitter, finds %d "
            + "sensors, all touching one";
    private static final String NO_CRYSTAL_LAID = "The mechanism should stand a volatile crystal at %s before the "
            + "sound, reads %s";
    private static final String CRYSTAL_STANDS = "The crystal at %s should be gone after the chain runs, reads %s";

    private static final int FLOOR_Y = 1;
    private static final int CHAIN_TICKS = 100;
    /** The first mechanism's sensor at the bay's west end, a sound beside it. */
    private static final BlockPos NEAR_SENSOR = new BlockPos(3, FLOOR_Y, 3);
    private static final BlockPos NEAR_SOUND = new BlockPos(2, FLOOR_Y, 3);
    /**
     * The second mechanism's sensor at the bay's east end, its resonator six blocks west, and a sound six blocks
     * further west: twelve from the sensor, past a sensor's eight, six from the resonator, inside its eight.
     */
    private static final BlockPos FAR_SENSOR = new BlockPos(36, FLOOR_Y, 3);
    private static final BlockPos FAR_SOUND = new BlockPos(24, FLOOR_Y, 3);
    private static final int RESONATOR_REACH = 6;

    private WarrenMechanismTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_WARRENS_WIRE_SENSORS_TO_VOLATILE_CRYSTALS,
                WarrenMechanismTests::lunarWarrensWireSensorsToVolatileCrystals);
        GooTestFunctions.registerTest(registrar, A_VIBRATION_SETS_OFF_A_MECHANISMS_CRYSTAL,
                WarrenMechanismTests::aVibrationSetsOffAMechanismsCrystal);
    }

    /**
     * Scans the warrens' band over a square of chunks in goo:lunar for sculk sensors: at least one touches a wireless
     * emitter whose closest block in goo:zap_receivers within 16 Manhattan is a volatile crystal, and at least one
     * touches no emitter.
     *
     * @param helper the gametest helper
     */
    public static void lunarWarrensWireSensorsToVolatileCrystals(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        int sensors = 0;
        int wired = 0;
        int armed = 0;
        for (BlockPos sensor : LunarWarrenTests.scanFor(lunar, Blocks.SCULK_SENSOR)) {
            if (sensor.getY() < LunarDepths.SEA_ZONE_TOP_Y || sensor.getY() >= LunarDepths.WARREN_ZONE_TOP_Y) {
                continue;
            }
            sensors++;
            Optional<BlockPos> emitter = Arrays.stream(Direction.values()).map(sensor::relative)
                    .filter(at -> lunar.getBlockState(at).is(GooBlocks.WIRELESS_EMITTER.get())).findFirst();
            if (emitter.isEmpty()) {
                continue;
            }
            wired++;
            Optional<BlockPos> closest = WirelessBeam.closest(emitter.get(),
                    at -> lunar.getBlockState(at).is(ZapDevice.RECEIVERS));
            if (closest.isPresent() && lunar.getBlockState(closest.get()).is(GooBlocks.VOLATILE_CRYSTAL.get())) {
                armed++;
            }
        }
        helper.assertTrue(armed > 0, String.format(NO_MECHANISM, sensors, wired));
        helper.assertTrue(sensors > wired, String.format(NO_BARE_SENSOR, sensors));
        helper.succeed();
    }

    /**
     * Lays the mechanism the warrens generate twice, each with its resonator: a sound beside the first sensor, and one
     * beyond the second sensor's range inside its resonator's; each mechanism's crystal is gone after the chain runs.
     *
     * @param helper the gametest helper
     */
    public static void aVibrationSetsOffAMechanismsCrystal(GameTestHelper helper) {
        WarrenMechanism near = new WarrenMechanism(Direction.EAST, Direction.NORTH, 2, 2,
                Optional.of(new BlockPos(0, 0, 2)));
        WarrenMechanism far = new WarrenMechanism(Direction.EAST, Direction.NORTH, 2, 2,
                Optional.of(new BlockPos(-RESONATOR_REACH, 0, 0)));
        near.lay(helper.getLevel(), helper.absolutePos(NEAR_SENSOR), Block.UPDATE_ALL);
        far.lay(helper.getLevel(), helper.absolutePos(FAR_SENSOR), Block.UPDATE_ALL);
        BlockPos nearCrystal = NEAR_SENSOR.offset(near.crystalOffsets().isEmpty() ? BlockPos.ZERO
                : near.crystalOffsets().getFirst());
        BlockPos farCrystal = FAR_SENSOR.offset(far.crystalOffsets().isEmpty() ? BlockPos.ZERO
                : far.crystalOffsets().getFirst());
        helper.runAfterDelay(1, () -> {
            for (BlockPos crystal : new BlockPos[] {nearCrystal, farCrystal}) {
                helper.assertTrue(helper.getBlockState(crystal).is(GooBlocks.VOLATILE_CRYSTAL.get()),
                        String.format(NO_CRYSTAL_LAID, crystal, helper.getBlockState(crystal)));
            }
            makeSound(helper, NEAR_SOUND);
            makeSound(helper, FAR_SOUND);
        });
        helper.runAfterDelay(CHAIN_TICKS, () -> {
            for (BlockPos crystal : new BlockPos[] {nearCrystal, farCrystal}) {
                helper.assertTrue(helper.getBlockState(crystal).isAir(),
                        String.format(CRYSTAL_STANDS, crystal, helper.getBlockState(crystal)));
            }
            helper.succeed();
        });
    }

    private static void makeSound(GameTestHelper helper, BlockPos at) {
        helper.getLevel().gameEvent(GameEvent.BLOCK_PLACE, Vec3.atCenterOf(helper.absolutePos(at)),
                GameEvent.Context.of(Blocks.STONE.defaultBlockState()));
    }
}
