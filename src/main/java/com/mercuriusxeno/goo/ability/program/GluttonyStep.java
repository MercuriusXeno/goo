package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.gluttony.Gluttony;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lays Gluttony on the host's target player: a point every interval while it
 * stands, filling hunger and health and banking them past their caps up to
 * max_overhunger and max_overheal, and finishes; a target that is not a
 * player has no bars to fill. Jelly Gluttony is {@code gluttony interval=80}.
 * A drunk brew lasts the brew's duration; the glove holds it with no expiry,
 * until the player ends it or runs dry.
 * gluttony-overheals-and-overhungers
 * self-effects-trickle-until-ended
 *
 * @param interval      the ticks between points
 * @param maxOverheal   the overheal cap, in health points
 * @param maxOverhunger the overhunger cap, in food points
 */
public record GluttonyStep(Expr interval, Expr maxOverheal, Expr maxOverhunger) implements Step {

    private static final String NAME = "gluttony";
    private static final String FIELD_INTERVAL = "interval";
    private static final String FIELD_MAX_OVERHEAL = "max_overheal";
    private static final String FIELD_MAX_OVERHUNGER = "max_overhunger";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<GluttonyStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_INTERVAL).forGetter(GluttonyStep::interval),
            Expr.CODEC.fieldOf(FIELD_MAX_OVERHEAL).forGetter(GluttonyStep::maxOverheal),
            Expr.CODEC.fieldOf(FIELD_MAX_OVERHUNGER).forGetter(GluttonyStep::maxOverhunger)
    ).apply(inst, GluttonyStep::new));

    /**
     * The registered type.
     */
    public static final StepType<GluttonyStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<GluttonyStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        if (host.target() instanceof Player player) {
            Gluttony standing = player.getData(GooAttachments.GLUTTONY);
            Gluttony caps = Gluttony.caps(interval.evaluateInt(context), maxOverheal.evaluateInt(context),
                    maxOverhunger.evaluateInt(context));
            long now = player.level().getGameTime();
            OptionalInt brewDuration = host.brewDuration();
            // brew-grants-the-self-ability-for-an-hour: the brew alone names a duration
            Gluttony laid = brewDuration.isPresent()
                    ? standing.apply(caps, brewDuration.getAsInt(), now)
                    : standing.hold(caps, now);
            player.setData(GooAttachments.GLUTTONY, laid);
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(interval, maxOverheal, maxOverhunger);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
