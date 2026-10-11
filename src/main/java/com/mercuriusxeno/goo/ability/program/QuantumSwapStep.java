package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.quantum.BodySwap;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Casts Quantum's swap for the host's player and finishes: with no swap body
 * standing it leaves one where they stand; with one standing it puts them
 * back in it and leaves a fresh one behind. Quantum's swap is {@code quantum_swap}.
 * swap-body-stands-until-reverted
 */
public record QuantumSwapStep() implements Step {

    private static final String NAME = "quantum_swap";

    /**
     * Codec for the step: it takes no params.
     */
    public static final MapCodec<QuantumSwapStep> CODEC = MapCodec.unit(QuantumSwapStep::new);

    /**
     * The registered type.
     */
    public static final StepType<QuantumSwapStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<QuantumSwapStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.hostAs(TargetHost.class).target() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level) {
            BodySwap.cast(level, player);
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}