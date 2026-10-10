package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.ability.program.ExtenderStep;
import com.mercuriusxeno.goo.ability.program.HeartOverlayStep;
import com.mercuriusxeno.goo.ability.program.LifetapStep;
import com.mercuriusxeno.goo.ability.program.LowerCaseEnumCodec;
import com.mercuriusxeno.goo.ability.program.LuxStep;
import com.mercuriusxeno.goo.ability.program.NourishStep;
import com.mercuriusxeno.goo.ability.program.SightStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The player state a held effect's program lays and its ending clears.
 * self-effects-trickle-until-ended
 */
public enum LaidState {
    /** A heart overlay over the health bar, laid by a heart_overlay step. */
    HEART_OVERLAY,
    /** Nourishment, laid by a nourish step. */
    NOURISH,
    /** Fungal sight (decision sight-lengthens-shift-and-outlines-fungus). */
    SIGHT,
    /** Lux's night vision and gaze glisten (decision lux-night-vision-without-particles). */
    LUX,
    /** The Extender's mark, laid by an extender step (decision extender-multiplies-the-next-self-duration). */
    EXTENDER,
    /** A lifetap (decision lifetap-trades-regen-for-leech). */
    LIFETAP;

    /** Codec for the saved state. */
    public static final Codec<LaidState> CODEC = LowerCaseEnumCodec.of(LaidState.class, "laid state");

    /** Codec for the state on the wire. */
    public static final StreamCodec<ByteBuf, LaidState> STREAM_CODEC =
            ByteBufCodecs.idMapper(ordinal -> values()[ordinal], LaidState::ordinal);

    /**
     * The state a program lays, read from its steps and every step beneath them.
     *
     * @param behaviors the ability's program
     * @return the state laid, empty for a program laying none
     */
    public static Set<LaidState> laidBy(List<Step> behaviors) {
        Set<LaidState> laid = EnumSet.noneOf(LaidState.class);
        behaviors.stream().flatMap(LaidState::withDescendants).map(LaidState::laidByStep)
                .flatMap(Optional::stream).forEach(laid::add);
        return laid;
    }

    /**
     * The state one step lays, if any.
     *
     * @param step the step
     * @return the state it lays, empty for a step laying none
     */
    private static Optional<LaidState> laidByStep(Step step) {
        return LAYING_STEPS.stream().filter(laying -> laying.kind().isInstance(step))
                .map(LayingStep::laid).findFirst();
    }

    /** The step kinds that lay state, each with the state it lays. */
    private static final List<LayingStep> LAYING_STEPS = List.of(
            new LayingStep(HeartOverlayStep.class, HEART_OVERLAY),
            new LayingStep(NourishStep.class, NOURISH),
            new LayingStep(SightStep.class, SIGHT),
            new LayingStep(LuxStep.class, LUX),
            new LayingStep(ExtenderStep.class, EXTENDER),
            new LayingStep(LifetapStep.class, LIFETAP));

    /**
     * A step kind and the state it lays.
     *
     * @param kind  the step's class
     * @param laid  the state it lays
     */
    private record LayingStep(Class<? extends Step> kind, LaidState laid) {
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(LaidState::withDescendants));
    }
}
