package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Grants the host's target fungal sight and finishes: Fungal Shift reaches
 * the factor farther and fungus shows through walls while it stands. Cast
 * from the glove it stands as a held effect, paying its upkeep until ended.
 * Sight is {@code sight factor=3} (decisions sight-lengthens-shift-and-outlines-fungus
 * and self-effects-trickle-until-ended).
 *
 * @param factor what Fungal Shift's range is multiplied by, evaluated when the step runs
 */
public record SightStep(Expr factor) implements Step {

    private static final String NAME = "sight";
    private static final String FIELD_FACTOR = "factor";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SightStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_FACTOR).forGetter(SightStep::factor)
    ).apply(inst, SightStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SightStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SightStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        float rangeFactor = factor.evaluateFloat(context);
        Sight standing = target.getData(GooAttachments.SIGHT);
        target.setData(GooAttachments.SIGHT, standing.hold(rangeFactor));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(factor);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
