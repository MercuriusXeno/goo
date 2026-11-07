package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Opens the invoking player's Pocket, the container that reserves void goo
 * for each slot its items fill. Void's Pocket is {@code pocket_open slot_cost=6}.
 * decision pocket-is-a-void-reserving-container-with-scroll-and-search
 *
 * @param slotCost the void each slot the stored items fill reserves
 */
public record PocketOpenStep(Expr slotCost) implements Step {

    private static final String NAME = "pocket_open";
    private static final String FIELD_SLOT_COST = "slot_cost";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PocketOpenStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_SLOT_COST).forGetter(PocketOpenStep::slotCost)
    ).apply(inst, PocketOpenStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PocketOpenStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PocketOpenStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(PocketHost.class).openPocket(slotCost.evaluateInt(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(slotCost);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.POCKET);
    }
}
