package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.ability.program.AirbornStep;
import com.mercuriusxeno.goo.ability.program.ExtenderStep;
import com.mercuriusxeno.goo.ability.program.HasteStep;
import com.mercuriusxeno.goo.ability.program.HeartOverlayStep;
import com.mercuriusxeno.goo.ability.program.LifetapStep;
import com.mercuriusxeno.goo.ability.program.LowerCaseEnumCodec;
import com.mercuriusxeno.goo.ability.program.LuxStep;
import com.mercuriusxeno.goo.ability.program.NourishStep;
import com.mercuriusxeno.goo.ability.program.ShifterStep;
import com.mercuriusxeno.goo.ability.program.SightStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.TelekinesisStep;
import com.mercuriusxeno.goo.ability.program.UndeadStep;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
    /** Counting as undead (decision undead-nether-hearts-burn-in-sunlight). */
    UNDEAD,
    /** Shifter (decision shifter-blinks-along-the-cursor-on-hit). */
    SHIFTER,
    /** The Extender's mark, laid by an extender step (decision extender-multiplies-the-next-self-duration). */
    EXTENDER,
    /** A lifetap (decision lifetap-trades-regen-for-leech). */
    LIFETAP,
    /** Haste's golden overlay (decision haste-stacks-speed-under-the-golden-overlay). */
    HASTE,
    /** Air control (decision airborn-steerable-levitation-and-soft-falls). */
    AIRBORN,
    /** Telekinesis's raised reach (decision telekinesis-enacts-at-extended-reach). */
    TELEKINESIS;

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
        behaviors.stream().flatMap(LaidState::withDescendants).forEach(step ->
                LAID_BY.forEach((kind, state) -> {
                    if (kind.isInstance(step)) {
                        laid.add(state);
                    }
                }));
        return laid;
    }

    /** The state each kind of step lays. */
    private static final Map<Class<? extends Step>, LaidState> LAID_BY = Map.ofEntries(
            Map.entry(HeartOverlayStep.class, HEART_OVERLAY),
            Map.entry(NourishStep.class, NOURISH),
            Map.entry(SightStep.class, SIGHT),
            Map.entry(LuxStep.class, LUX),
            Map.entry(UndeadStep.class, UNDEAD),
            Map.entry(ShifterStep.class, SHIFTER),
            Map.entry(ExtenderStep.class, EXTENDER),
            Map.entry(LifetapStep.class, LIFETAP),
            Map.entry(HasteStep.class, HASTE),
            Map.entry(AirbornStep.class, AIRBORN),
            Map.entry(TelekinesisStep.class, TELEKINESIS));

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(LaidState::withDescendants));
    }
}
