package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Charms the host's target for the player who threw the goo and finishes:
 * until the charm fades, the target fights whatever targets that player and
 * follows them otherwise. A throw with no player behind it charms nothing.
 * Hex charm is {@code charm duration="6000 * pow(20 / health, 0.4)"}.
 * charm-glisten-and-icon-over-the-head
 *
 * @param duration the ticks the charm lasts, evaluated when the step runs
 */
public record CharmStep(Expr duration) implements Step {

    private static final String NAME = "charm";
    private static final String FIELD_DURATION = "duration";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<CharmStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(CharmStep::duration)
    ).apply(inst, CharmStep::new));

    /**
     * The registered type.
     */
    public static final StepType<CharmStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<CharmStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        if (host.thrower() instanceof Player charmer) {
            LivingEntity target = host.target();
            long expiresAt = target.level().getGameTime() + duration.evaluateInt(context);
            target.setData(GooAttachments.CHARMED, new Charmed(charmer.getUUID(), expiresAt));
        }
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
