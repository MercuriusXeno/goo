package com.mercuriusxeno.goo.ability.pulse;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WeatheringCopperDoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Zap's device table: which row ticks each kind of block, read by
 * class so no registry boots, and which blocks a Zap ticks rather than
 * dispersing into the Signal wave.
 * zap-ticks-the-device-and-stuns
 * zap-disperses-into-signal
 */
class ZapDeviceTest {

    static Stream<Arguments> rows() {
        return Stream.of(
                Arguments.of(LeverBlock.class, ZapDevice.LEVER),
                Arguments.of(ButtonBlock.class, ZapDevice.BUTTON),
                Arguments.of(DoorBlock.class, ZapDevice.DOOR),
                Arguments.of(WeatheringCopperDoorBlock.class, ZapDevice.DOOR),
                Arguments.of(TrapDoorBlock.class, ZapDevice.OPENABLE),
                Arguments.of(FenceGateBlock.class, ZapDevice.OPENABLE),
                Arguments.of(RepeaterBlock.class, ZapDevice.REPEATER),
                Arguments.of(ComparatorBlock.class, ZapDevice.POWER_SOURCE),
                Arguments.of(RedStoneWireBlock.class, ZapDevice.POWER_SOURCE),
                Arguments.of(RedstoneLampBlock.class, ZapDevice.POWER_SOURCE),
                Arguments.of(PistonBaseBlock.class, ZapDevice.POWER_SOURCE),
                Arguments.of(Block.class, ZapDevice.POWER_SOURCE));
    }

    @ParameterizedTest
    @MethodSource("rows")
    void eachBlockFallsUnderItsRow(Class<? extends Block> block, ZapDevice row) {
        assertEquals(row, ZapDevice.of(block), block.getSimpleName());
    }

    @Test
    void aBlockWithARowOfItsOwnTicks() {
        assertTrue(ZapDevice.ticks(ZapDevice.LEVER, false));
        assertTrue(ZapDevice.ticks(ZapDevice.REPEATER, false));
    }

    @Test
    void aReceiverTakesThePowerSource() {
        assertTrue(ZapDevice.ticks(ZapDevice.POWER_SOURCE, true));
    }

    @Test
    void aPlainBlockDisperses() {
        assertFalse(ZapDevice.ticks(ZapDevice.POWER_SOURCE, false));
    }
}
