package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.nourish.Nourish;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Nourishes the host's target player: a food point every interval for the
 * duration, standing nourishment stacking the duration onto its expiry, and
 * finishes; a target that is not a player has no hunger to fill. Vital
 * Nourish is {@code nourish interval=80 duration=400}, and a drunk brew's
 * duration stands in for the step's own.
 * nourish-restores-hunger-over-time
 *
 * @param interval the ticks between food points
 * @param duration the nourishment's duration in ticks, evaluated when the step runs outside a brew
 */
public record NourishStep(Expr interval, Expr duration) implements Step {

    private static final String NAME = "nourish";
    private static final String FIELD_INTERVAL = "interval";
    private static final String FIELD_DURATION = "duration";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<NourishStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_INTERVAL).forGetter(NourishStep::interval),
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(NourishStep::duration)
    ).apply(inst, NourishStep::new));

    /**
     * The registered type.
     */
    public static final StepType<NourishStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<NourishStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        if (host.target() instanceof Player player) {
            // brew-grants-the-self-ability-for-an-hour: the brew's duration replaces the step's own
            int ticks = host.brewDuration().orElseGet(() -> duration.evaluateInt(context));
            Nourish standing = player.getData(GooAttachments.NOURISH);
            player.setData(GooAttachments.NOURISH, standing.apply(interval.evaluateInt(context), ticks,
                    player.level().getGameTime()));
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(interval, duration);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
