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
 * Opens a wormhole from the struck face to the first opening ahead and
 * finishes: a mouth on the face and one at the opening, each carrying whoever
 * steps in out of the other until the lifetime runs out. A throw at a face
 * with no opening within reach is refused before it is paid, the face
 * showing the hole collapse. Quantum's hole is
 * {@code wormhole reach=32 lifetime=600}.
 * hole-closes-the-space-to-an-opening-ahead
 *
 * @param reach    how many cells past the struck block the scan runs; a literal, read before the throw is paid
 * @param lifetime the ticks the mouths stand, evaluated when the step runs
 */
public record WormholeStep(Expr reach, Expr lifetime) implements TargetGatedStep {

    private static final String NAME = "wormhole";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_LIFETIME = "lifetime";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<WormholeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_REACH).forGetter(WormholeStep::reach),
            Expr.CODEC.fieldOf(FIELD_LIFETIME).forGetter(WormholeStep::lifetime)
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
        context.hostAs(WormholeHost.class).openHole(reach.evaluateInt(context), lifetime.evaluateInt(context));
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
        return Stream.of(reach, lifetime);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.WORMHOLE);
    }
}
