package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Charges the host's target: a cast adds its duration onto whatever Charged
 * the target still holds, so casts stack, and a drunk brew charges it for
 * the brew's hour, past any shorter charge it held. While charged, the
 * target's channeled abilities take their JSON's Charged multipliers.
 * decision charged-scales-channel-params-by-json
 *
 * @param duration the ticks one cast charges for, evaluated when the step runs outside a brew
 */
public record ChargedStep(Expr duration) implements Step {

    private static final String NAME = "charged";
    private static final String FIELD_DURATION = "duration";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ChargedStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(ChargedStep::duration)
    ).apply(inst, ChargedStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ChargedStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The game time a charge runs until after a cast: the cast's ticks added
     * onto what still stands of the last charge, or onto now.
     *
     * @param chargedUntil the game time the target's charge ran until
     * @param now          the game time
     * @param ticks        the cast's ticks
     * @return the new end of the charge
     */
    public static long stackedUntil(long chargedUntil, long now, int ticks) {
        return Math.max(chargedUntil, now) + ticks;
    }

    /**
     * The game time a charge runs until after a brew: the brew's hour from
     * now, or the standing charge when that runs longer.
     *
     * @param chargedUntil the game time the target's charge ran until
     * @param now          the game time
     * @param brewTicks    the brew's ticks
     * @return the new end of the charge
     */
    public static long brewedUntil(long chargedUntil, long now, int brewTicks) {
        return Math.max(chargedUntil, now + brewTicks);
    }

    /**
     * Whether an entity stands charged now.
     *
     * @param entity the entity
     * @return true while its charge runs past the current game time
     */
    public static boolean isCharged(LivingEntity entity) {
        return entity.getData(GooAttachments.CHARGED) > entity.level().getGameTime();
    }

    @Override
    public StepType<ChargedStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        long now = target.level().getGameTime();
        long chargedUntil = target.getData(GooAttachments.CHARGED);
        long until = host.brewDuration().isPresent()
                ? brewedUntil(chargedUntil, now, host.brewDuration().getAsInt())
                : stackedUntil(chargedUntil, now, duration.evaluateInt(context));
        target.setData(GooAttachments.CHARGED, until);
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(duration);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
