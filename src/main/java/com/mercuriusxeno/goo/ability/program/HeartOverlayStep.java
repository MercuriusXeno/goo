package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lays a heart overlay over the host's target player's health bar and
 * finishes; a target that is not a player has no health bar to lay it on and
 * is left alone. Blaze Kindle is {@code heart_overlay kind=kindle} (decision
 * overlay-hearts-are-an-elemental-overshield). A drunk brew lays it for the
 * brew's duration; the glove lays it with no expiry, held until the player
 * ends it or runs dry.
 * self-effects-trickle-until-ended
 *
 * Rock Stoneskin names {@code damage_taken}, the share of a physical hit its
 * stone takes (decision stoneskin-stone-hearts-block-regeneration).
 *
 * @param kind        the overlay's kind
 * @param damageTaken the share of a physical hit a half of shield takes
 */
public record HeartOverlayStep(HeartKind kind, float damageTaken) implements Step {

    private static final String NAME = "heart_overlay";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_DAMAGE_TAKEN = "damage_taken";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<HeartOverlayStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            HeartKind.CODEC.fieldOf(FIELD_KIND).forGetter(HeartOverlayStep::kind),
            Codec.FLOAT.optionalFieldOf(FIELD_DAMAGE_TAKEN, HeartOverlay.WHOLE_HIT)
                    .forGetter(HeartOverlayStep::damageTaken)
    ).apply(inst, HeartOverlayStep::new));

    /**
     * A step whose shields take every hit whole.
     *
     * @param kind the overlay's kind
     */
    public HeartOverlayStep(HeartKind kind) {
        this(kind, HeartOverlay.WHOLE_HIT);
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
            HeartOverlay standing = player.getData(GooAttachments.HEART_OVERLAY);
            long now = player.level().getGameTime();
            OptionalInt brewDuration = host.brewDuration();
            // brew-grants-the-self-ability-for-an-hour: the brew alone names a duration
            HeartOverlay laid = brewDuration.isPresent()
                    ? standing.apply(kind, brewDuration.getAsInt(), player.getHealth(), player.getMaxHealth(),
                            damageTaken, now)
                    : standing.hold(kind, player.getHealth(), player.getMaxHealth(), damageTaken, now);
            player.setData(GooAttachments.HEART_OVERLAY, laid);
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
