package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.block.canister.CanisterSlotFluidHandler;
import com.mercuriusxeno.goo.block.gasket.GasketDemand;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * What a gasketed tap receives from its partner: one drip at a time, asked
 * at the rate the valve sets while the tap's own canister holds no goo, and
 * dripped from here once that canister is empty (decisions
 * receivers-demand-and-links-relay and tap-asks-gasket-partner-per-drip).
 */
public final class TapGasketIntake extends CanisterSlotFluidHandler implements GasketDemand {

    /** The most one drip draws, at the fastest grade. */
    static final int CAPACITY = TapDripGrade.FOUR_PER_TICK.dripVolume();

    private final IntSupplier dripVolume;
    private final BooleanSupplier asking;

    /**
     * @param dripVolume   the mB one drip draws at the valve's grade
     * @param asking       whether the tap asks its partner now: valve open, canister holding no goo
     * @param onChange     called when the intake's contents change
     * @param tickSupplier supplies the game tick for stream timing
     * @param admits       answers whether an arriving fluid is goo the tap can drip
     */
    public TapGasketIntake(IntSupplier dripVolume, BooleanSupplier asking, Runnable onChange,
                           LongSupplier tickSupplier, Predicate<FluidResource> admits) {
        super(CAPACITY, onChange, tickSupplier, admits);
        this.dripVolume = dripVolume;
        this.asking = asking;
    }

    /**
     * One drip's volume less what the intake already holds, while the tap asks; nothing otherwise.
     */
    @Override
    public OptionalInt statedDemand(FluidResource resource) {
        if (!asking.getAsBoolean()) {
            return OptionalInt.of(0);
        }
        return OptionalInt.of(Math.max(0, dripVolume.getAsInt() - totalVolume()));
    }
}
