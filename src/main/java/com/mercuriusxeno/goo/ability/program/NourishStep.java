package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.nourish.Nourish;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Nourishes the host's target player: a food point every interval while it
 * stands, and finishes; a target that is not a player has no hunger to fill.
 * Vital Nourish is {@code nourish interval=80}. A drunk brew nourishes for
 * the brew's duration; the glove nourishes with no expiry, held until the
 * player ends it or runs dry.
 * nourish-restores-hunger-over-time
 * self-effects-trickle-until-ended
 *
 * @param interval the ticks between food points
 */
public record NourishStep(Expr interval) implements Step {

    private static final String NAME = "nourish";
    private static final String FIELD_INTERVAL = "interval";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<NourishStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_INTERVAL).forGetter(NourishStep::interval)
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
            Nourish standing = player.getData(GooAttachments.NOURISH);
            int pointInterval = interval.evaluateInt(context);
            long now = player.level().getGameTime();
            OptionalInt brewDuration = host.brewDuration();
            // brew-grants-the-self-ability-for-an-hour: the brew alone names a duration
            Nourish laid = brewDuration.isPresent()
                    ? standing.apply(pointInterval, brewDuration.getAsInt(), now)
                    : standing.hold(pointInterval, now);
            player.setData(GooAttachments.NOURISH, laid);
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(interval);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
