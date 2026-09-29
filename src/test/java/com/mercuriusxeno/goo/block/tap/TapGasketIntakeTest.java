package com.mercuriusxeno.goo.block.tap;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

/**
 * A gasketed tap's intake asks its partner one drip at the valve's grade while the tap
 * asks, and nothing otherwise (decision tap-asks-gasket-partner-per-drip). The vanilla
 * bootstrap stands the empty fluid stack the intake's tank starts from.
 */
class TapGasketIntakeTest {

    private static final FluidResource ANY_GOO = mock(FluidResource.class);

    @BeforeAll
    static void standVanillaFluids() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    private static TapGasketIntake intake(TapDripGrade grade, AtomicBoolean asking) {
        return new TapGasketIntake(grade::dripVolume, asking::get, () -> { }, () -> 0L, resource -> true);
    }

    @Test
    void aTapNotAskingStatesNoDemand() {
        TapGasketIntake intake = intake(TapDripGrade.FOUR_PER_TICK, new AtomicBoolean(false));

        assertEquals(OptionalInt.of(0), intake.statedDemand(ANY_GOO));
    }

    @ParameterizedTest
    @EnumSource(TapDripGrade.class)
    void anEmptyIntakeAsksOneDripAtTheValveGrade(TapDripGrade grade) {
        TapGasketIntake intake = intake(grade, new AtomicBoolean(true));

        assertEquals(OptionalInt.of(grade.dripVolume()), intake.statedDemand(ANY_GOO));
    }

    @Test
    void anIntakeHoldingPartOfADripAsksTheRest() {
        TapGasketIntake intake = spy(intake(TapDripGrade.FOUR_PER_TICK, new AtomicBoolean(true)));
        doReturn(1).when(intake).getAmount();

        assertEquals(OptionalInt.of(TapDripGrade.FOUR_PER_TICK.dripVolume() - 1), intake.statedDemand(ANY_GOO));
    }

    @Test
    void theDemandFollowsWhetherTheTapAsksNow() {
        AtomicBoolean asking = new AtomicBoolean(false);
        TapGasketIntake intake = intake(TapDripGrade.ONE_PER_TICK, asking);
        OptionalInt whileCanisterHoldsGoo = intake.statedDemand(ANY_GOO);
        asking.set(true);

        assertEquals(OptionalInt.of(0), whileCanisterHoldsGoo);
        assertEquals(OptionalInt.of(1), intake.statedDemand(ANY_GOO));
    }

    @Test
    void anEmptyIntakeDrawsNoDrip() {
        TapGasketIntake intake = intake(TapDripGrade.FOUR_PER_TICK, new AtomicBoolean(true));

        assertNull(TapDrip.drawIntake(intake, TapDripGrade.FOUR_PER_TICK.dripVolume()));
    }
}
