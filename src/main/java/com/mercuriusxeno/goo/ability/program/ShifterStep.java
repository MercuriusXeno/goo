package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.zone.Shifter;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Grants the host's target shifter and finishes: each hit it would
 * take blinks it the distance along its look instead. Cast from the glove
 * it stands as a held effect, paying its upkeep until ended; drunk as a brew
 * it stands for the brew's hour. Shifter is {@code shifter distance=8}
 * (decisions shifter-blinks-along-the-cursor-on-hit,
 * self-effects-trickle-until-ended and brew-grants-the-self-ability-for-an-hour).
 *
 * @param distance how far each hit blinks the target, evaluated when the step runs
 */
public record ShifterStep(Expr distance) implements Step {

    private static final String NAME = "shifter";
    private static final String FIELD_DISTANCE = "distance";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ShifterStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_DISTANCE).forGetter(ShifterStep::distance)
    ).apply(inst, ShifterStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ShifterStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ShifterStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        float blinkDistance = distance.evaluateFloat(context);
        Shifter standing = target.getData(GooAttachments.SHIFTER);
        OptionalInt brew = host.brewDuration();
        target.setData(GooAttachments.SHIFTER, brew.isPresent()
                ? standing.brew(blinkDistance, brew.getAsInt(), target.level().getGameTime())
                : standing.hold(blinkDistance));
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
