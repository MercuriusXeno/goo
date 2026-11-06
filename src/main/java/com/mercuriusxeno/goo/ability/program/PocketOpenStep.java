package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Opens the invoking player's Pocket, the container that reserves void goo
 * for what it holds. Void's Pocket is {@code pocket_open}.
 * decision pocket-is-a-void-reserving-container-with-scroll-and-search
 */
public record PocketOpenStep() implements Step {

    private static final String NAME = "pocket_open";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<PocketOpenStep> CODEC = MapCodec.unit(PocketOpenStep::new);

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
        context.hostAs(PocketHost.class).openPocket();
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.POCKET);
    }
}
