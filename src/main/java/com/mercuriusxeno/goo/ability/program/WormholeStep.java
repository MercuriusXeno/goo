package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.quantum.Wormhole;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Carries the caster through a wormhole from the struck face to the first
 * opening ahead and finishes. A throw at a face with no opening within reach
 * is refused before it is paid, the face showing the hole collapse.
 * Quantum's hole is {@code wormhole reach=16}.
 * hole-closes-the-space-to-an-opening-ahead
 *
 * @param reach how many cells past the struck block the scan runs; a literal, read before the throw is paid
 */
public record WormholeStep(Expr reach) implements TargetGatedStep {

    private static final String NAME = "wormhole";
    private static final String FIELD_REACH = "reach";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<WormholeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_REACH).forGetter(WormholeStep::reach)
    ).apply(inst, WormholeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<WormholeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<WormholeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(WormholeHost.class).carryThrough(reach.evaluateInt(context));
        return true;
    }

    @Override
    public boolean admitsFace(ServerPlayer thrower, BlockPos struck, Direction face) {
        return Wormhole.opens(thrower.level(), thrower, struck, face, reach.evaluateInt(Variables.NONE));
    }

    @Override
    public void refuseAtFace(ServerPlayer thrower, BlockPos struck, Direction face) {
        Wormhole.collapse(thrower.level(), struck, face);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(reach);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.WORMHOLE);
    }
}
