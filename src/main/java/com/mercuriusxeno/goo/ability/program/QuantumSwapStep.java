package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.quantum.BodySwap;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Casts Quantum's swap for the host's player and finishes: with no swap body
 * standing it blinks them to the spot their look reaches in sight and leaves
 * a body where they stood; with one standing it puts them back in it and
 * leaves a fresh one behind. Quantum's swap is {@code quantum_swap range=32}.
 * swap-body-stands-where-you-aim
 * swap-body-stands-until-reverted
 *
 * @param range how far the first cast's blink reaches, in blocks, evaluated when the step runs
 */
public record QuantumSwapStep(Expr range) implements Step {

    private static final String NAME = "quantum_swap";
    private static final String FIELD_RANGE = "range";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<QuantumSwapStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RANGE).forGetter(QuantumSwapStep::range)
    ).apply(inst, QuantumSwapStep::new));

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
            BodySwap.cast(level, player, blinkTo(player, range.evaluate(context)));
        }
        return true;
    }

    /**
     * A first cast with nowhere in sight to land neither runs nor drains; a
     * cast with a body standing always swaps back.
     * swap-body-stands-where-you-aim
     */
    @Override
    public boolean admits(StepContext context) {
        return !(context.hostAs(TargetHost.class).target() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level)
                || BodySwap.standingBody(level, player).isPresent()
                || blinkTo(player, range.evaluate(context)).isPresent();
    }

    private static Optional<Vec3> blinkTo(ServerPlayer player, double reach) {
        return BlinkResolver.inSight(new LevelBlinkSpace(player.level(), player), player.position(),
                player.getLookAngle(), reach, BlinkBody.of(player)).map(BlinkLanding::feet);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(range);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
