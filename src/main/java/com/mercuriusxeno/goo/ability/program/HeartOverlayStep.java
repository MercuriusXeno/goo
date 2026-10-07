package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lays a heart overlay over the host's target player's health bar and
 * finishes; a target that is not a player has no health bar to lay it on and
 * is left alone. Blaze Kindle is
 * {@code heart_overlay kind=kindle duration=1200} (decision
 * overlay-hearts-are-an-elemental-overshield). A drunk brew's duration
 * stands in for the step's own.
 *
 * Rock Stoneskin names {@code damage_taken}, the share of a physical hit its
 * stone takes (decision stoneskin-stone-hearts-block-regeneration).
 *
 * @param kind        the overlay's kind
 * @param duration    the overlay's duration in ticks, evaluated when the step runs outside a brew
 * @param damageTaken the share of a physical hit a half of shield takes
 */
public record HeartOverlayStep(HeartKind kind, Expr duration, float damageTaken) implements Step {

    private static final String NAME = "heart_overlay";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_DURATION = "duration";
    private static final String FIELD_DAMAGE_TAKEN = "damage_taken";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<HeartOverlayStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            HeartKind.CODEC.fieldOf(FIELD_KIND).forGetter(HeartOverlayStep::kind),
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(HeartOverlayStep::duration),
            Codec.FLOAT.optionalFieldOf(FIELD_DAMAGE_TAKEN, HeartOverlay.WHOLE_HIT)
                    .forGetter(HeartOverlayStep::damageTaken)
    ).apply(inst, HeartOverlayStep::new));

    /**
     * A step whose shields take every hit whole.
     *
     * @param kind     the overlay's kind
     * @param duration the overlay's duration in ticks
     */
    public HeartOverlayStep(HeartKind kind, Expr duration) {
        this(kind, duration, HeartOverlay.WHOLE_HIT);
    }

    /**
     * The registered type.
     */
    public static final StepType<HeartOverlayStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<HeartOverlayStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        if (host.target() instanceof Player player) {
            // decision brew-grants-the-self-ability-for-an-hour
            int ticks = host.brewDuration().orElseGet(() -> duration.evaluateInt(context));
            HeartOverlay standing = player.getData(GooAttachments.HEART_OVERLAY);
            player.setData(GooAttachments.HEART_OVERLAY, standing.apply(kind, ticks,
                    player.getHealth(), player.getMaxHealth(), damageTaken, player.level().getGameTime()));
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
