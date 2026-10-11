package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.quantum.AnchorTravel;
import com.mercuriusxeno.goo.ability.quantum.QuantumAnchors;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Makes the prism a quantum anchor for as long as it stands: on its first
 * tick it stands for its caster, pairing with the caster's anchor waiting
 * for one, and every tick it carries a player standing at it to its
 * partner. Never finishes. Quantum's anchor is {@code quantum_anchor}.
 * quantum-anchors-link-two-points
 */
public record QuantumAnchorStep() implements Step {

    private static final String NAME = "quantum_anchor";

    /**
     * Codec for the step: it takes no params.
     */
    public static final MapCodec<QuantumAnchorStep> CODEC = MapCodec.unit(QuantumAnchorStep::new);

    /**
     * The registered type.
     */
    public static final StepType<QuantumAnchorStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<QuantumAnchorStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LevelHost host = context.hostAs(LevelHost.class);
        ServerLevel level = host.level();
        BlockPos pos = host.position();
        if (context.stepTicks() == 0 && level.getBlockEntity(pos) instanceof PrismBlockEntity prism) {
            UUID caster = prism.caster();
            if (caster != null) {
                QuantumAnchors.get(level).stand(caster, GlobalPos.of(level.dimension(), pos));
            }
        }
        AnchorTravel.carryFrom(level, pos);
        return false;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.LEVEL, HostCapability.TICKING);
    }
}
