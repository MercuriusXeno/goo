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
 * Lays Gluttony on the host's target player and finishes: while it stands,
 * healing and eating past full bars bank as overheal and overhunger up to
 * max_overheal and max_overhunger; a target that is not a player has no bars.
 * A drunk brew lasts the brew's duration; the glove holds it with no expiry,
 * until the player ends it or runs dry.
 * gluttony-overheals-and-overhungers
 * self-effects-trickle-until-ended
 *
 * @param maxOverheal   the overheal cap, in health points
 * @param maxOverhunger the overhunger cap, in food points
 */
public record GluttonyStep(Expr maxOverheal, Expr maxOverhunger) implements Step {

    private static final String NAME = "gluttony";
    private static final String FIELD_MAX_OVERHEAL = "max_overheal";
    private static final String FIELD_MAX_OVERHUNGER = "max_overhunger";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<GluttonyStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
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
            Gluttony caps = Gluttony.caps(maxOverheal.evaluateInt(context), maxOverhunger.evaluateInt(context));
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
        return Stream.of(maxOverheal, maxOverhunger);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
