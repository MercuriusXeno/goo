package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.ability.program.AirbornStep;
import com.mercuriusxeno.goo.ability.program.EludeStep;
import com.mercuriusxeno.goo.ability.program.GluttonyStep;
import com.mercuriusxeno.goo.ability.program.HeartOverlayStep;
import com.mercuriusxeno.goo.ability.program.HerdStep;
import com.mercuriusxeno.goo.ability.program.LowerCaseEnumCodec;
import com.mercuriusxeno.goo.ability.program.LuxStep;
import com.mercuriusxeno.goo.ability.program.RemoteStep;
import com.mercuriusxeno.goo.ability.program.SculkerStep;
import com.mercuriusxeno.goo.ability.program.SightStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.UndeadStep;
import com.mercuriusxeno.goo.ability.program.UnveilStep;
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
    /** Gluttony and its banks, laid by a gluttony step (decision gluttony-overheals-and-overhungers). */
    GLUTTONY,
    /** Fungal sight (decision sight-lengthens-shift-and-outlines-fungus). */
    SIGHT,
    /** Lux's night vision and gaze glisten (decision lux-night-vision-without-particles). */
    LUX,
    /** Counting as undead (decision undead-nether-hearts-burn-in-sunlight). */
    UNDEAD,
    /** Elude (decision elude-moves-you-to-a-random-safe-spot-on-mob-or-projectile-damage). */
    ELUDE,
    /**
     * Laid by no step, Extend having left the mod for Signal (decisions
     * extender-multiplies-the-next-self-duration, extend-is-dropped-for-signal-an-in-world-pulse-goo);
     * it stands so a saved held effect naming it still loads.
     */
    EXTEND,
    /** Float's flight (decision tailwind-and-airborn-merge-into-float). */
    AIRBORN,
    /** Remote's raised reach (decision telekinesis-enacts-at-extended-reach). */
    REMOTE,
    /** The eldritch state (decision eldritch-sight-reveals-the-out-of-phase). */
    UNVEIL,
    /**
     * Laid by no step, Ancient having left the mod (decision yore-leaves-and-rewind-moves-to-xeno-as-revert);
     * it stands so a saved held effect naming it still loads.
     */
    ANCIENT,
    /**
     * Standing out of phase, laid by no step, Phase goo phasing what stands in
     * it (decision phase-goo-puts-what-stands-in-it-out-of-phase); it stands so
     * a saved held effect naming it still loads and steps its player back into phase.
     */
    PHASE,
    /**
     * Laid by no step, Lead being a mob ability (decision rally-is-a-mob-effect-on-one-target);
     * it stands so a saved held effect naming it still loads.
     */
    LEAD,
    /** The dive's swim through stone and its vision (decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks). */
    SCULKER,
    /** Yarn's Herd, last since the client sync numbers states by order (decision leader-is-zoos-brew-ability). */
    HERD;

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
            Map.entry(GluttonyStep.class, GLUTTONY),
            Map.entry(SightStep.class, SIGHT),
            Map.entry(LuxStep.class, LUX),
            Map.entry(UndeadStep.class, UNDEAD),
            Map.entry(EludeStep.class, ELUDE),
            Map.entry(AirbornStep.class, AIRBORN),
            Map.entry(RemoteStep.class, REMOTE),
            Map.entry(UnveilStep.class, UNVEIL),
            Map.entry(SculkerStep.class, SCULKER),
            Map.entry(HerdStep.class, HERD));

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(LaidState::withDescendants));
    }
}
