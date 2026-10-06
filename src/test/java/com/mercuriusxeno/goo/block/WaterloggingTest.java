package com.mercuriusxeno.goo.block;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The one SimpleWaterloggedBlock idiom (decision waterlogging-idiom-lives-once): a
 * waterlogged state answers a water source and schedules a water tick, a dry state
 * answers its fallback and schedules nothing. The vanilla bootstrap stands the water
 * fluid and the WATERLOGGED property.
 */
class WaterloggingTest {

    private static final BlockPos POS = new BlockPos(1, 2, 3);

    /**
     * The vanilla bootstrap asks FML whether it runs in production, which a test JVM
     * cannot answer, so a stubbed loader answers it while the bootstrap stands the fluids.
     */
    @BeforeAll
    static void standVanillaFluids() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    private static BlockState stateWaterlogged(boolean waterlogged) {
        BlockState state = mock(BlockState.class);
        when(state.getValue(BlockStateProperties.WATERLOGGED)).thenReturn(waterlogged);
        return state;
    }

    @Nested
    class FluidStateOfTheBlock {

        @Test
        void waterloggedStateAnswersTheWaterSource() {
            FluidState fallback = mock(FluidState.class);

            assertSame(Fluids.WATER.getSource(false), Waterlogging.fluidState(stateWaterlogged(true), fallback));
        }

        @Test
        void dryStateAnswersTheFallback() {
            FluidState fallback = mock(FluidState.class);

            assertSame(fallback, Waterlogging.fluidState(stateWaterlogged(false), fallback));
        }
    }

    @Nested
    class WaterTick {

        @Test
        void waterloggedStateSchedulesAWaterTickAtTheBlock() {
            ScheduledTickAccess ticks = mock(ScheduledTickAccess.class);
            LevelReader level = mock(LevelReader.class);

            Waterlogging.scheduleWaterTick(stateWaterlogged(true), ticks, POS, level);

            verify(ticks).scheduleTick(POS, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        @Test
        void dryStateSchedulesNothing() {
            ScheduledTickAccess ticks = mock(ScheduledTickAccess.class);

            Waterlogging.scheduleWaterTick(stateWaterlogged(false), ticks, POS, mock(LevelReader.class));

            verify(ticks, never()).scheduleTick(any(BlockPos.class), eq(Fluids.WATER), anyInt());
        }
    }
}
