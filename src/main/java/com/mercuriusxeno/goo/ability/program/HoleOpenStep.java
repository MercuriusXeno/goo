package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hole.HoleOpening;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Opens the caster's portable hole on the struck face and finishes: on a top
 * face it swallows what is thrown in onto the caster's shelf, on a bottom face
 * it pours the shelf out. A throw at a wall's face is refused before it is
 * paid. Void's hole is {@code hole_open lifetime=600}.
 * decision hole-is-an-in-world-portable-hole-for-items
 *
 * @param lifetime the ticks the hole stands, evaluated when the step runs
 */
public record HoleOpenStep(Expr lifetime) implements TargetGatedStep {

    private static final String NAME = "hole_open";
    private static final String FIELD_LIFETIME = "lifetime";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<HoleOpenStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_LIFETIME).forGetter(HoleOpenStep::lifetime)
    ).apply(inst, HoleOpenStep::new));

    /**
     * The registered type.
     */
    public static final StepType<HoleOpenStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<HoleOpenStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(HoleHost.class).openPortableHole(lifetime.evaluateInt(context));
        return true;
    }

    @Override
    public boolean admitsFace(ServerPlayer thrower, BlockPos struck, Direction face) {
        return HoleOpening.takes(face);
    }

    @Override
    public void refuseAtFace(ServerPlayer thrower, BlockPos struck, Direction face) {
        HoleOpening.refuse(thrower.level(), struck, face);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(lifetime);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.HOLE);
    }
}
