package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.zone.Elude;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Grants the host's target Elude and finishes: each mob or projectile hit it
 * would take moves it instead to a random safe spot within the distance. Cast
 * from the glove it stands as a held effect, paying its upkeep until ended.
 * Elude is {@code elude distance=8} (decisions
 * elude-moves-you-to-a-random-safe-spot-on-mob-or-projectile-damage and
 * self-effects-trickle-until-ended).
 *
 * @param distance the radius each escape lands within, evaluated when the step runs
 */
public record EludeStep(Expr distance) implements Step {

    private static final String NAME = "elude";
    private static final String FIELD_DISTANCE = "distance";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<EludeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_DISTANCE).forGetter(EludeStep::distance)
    ).apply(inst, EludeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<EludeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<EludeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        float blinkDistance = distance.evaluateFloat(context);
        Elude standing = target.getData(GooAttachments.ELUDE);
        target.setData(GooAttachments.ELUDE, standing.hold(blinkDistance));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(distance);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
