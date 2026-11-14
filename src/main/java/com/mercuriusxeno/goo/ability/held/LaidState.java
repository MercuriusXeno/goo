package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.ability.program.AirbornStep;
import com.mercuriusxeno.goo.ability.program.AncientStep;
import com.mercuriusxeno.goo.ability.program.EludeStep;
import com.mercuriusxeno.goo.ability.program.ExtendStep;
import com.mercuriusxeno.goo.ability.program.GluttonyStep;
import com.mercuriusxeno.goo.ability.program.HeartOverlayStep;
import com.mercuriusxeno.goo.ability.program.LeaderStep;
import com.mercuriusxeno.goo.ability.program.LifetapStep;
import com.mercuriusxeno.goo.ability.program.LowerCaseEnumCodec;
import com.mercuriusxeno.goo.ability.program.LuxStep;
import com.mercuriusxeno.goo.ability.program.PhaseStep;
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
    /** The Extend's mark, laid by an extend step (decision extender-multiplies-the-next-self-duration). */
    EXTEND,
    /** A lifetap (decision lifetap-trades-regen-for-leech). */
    LIFETAP,
    /** Float's flight (decision tailwind-and-airborn-merge-into-float). */
    AIRBORN,
    /** Remote's raised reach (decision telekinesis-enacts-at-extended-reach). */
    REMOTE,
    /** The eldritch state (decision eldritch-sight-reveals-the-out-of-phase). */
    UNVEIL,
    /** Ancient's floor of half a heart and its aged overlay (decision ancient-makes-the-player-immortal). */
    ANCIENT,
    /** Standing out of phase (decision phase-shares-a-plane-between-the-phased). */
    PHASE,
    /**
     * Laid by no step, Rally being a mob ability (decision rally-is-a-mob-effect-on-one-target);
     * it stands so a saved held effect naming it still loads.
     */
    RALLY,
    /** The dive's swim through stone and its vision (decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks). */
    SCULKER,
    /** Zoo's Leader, last since the client sync numbers states by order (decision leader-is-zoos-brew-ability). */
    LEADER;

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
            Map.entry(ExtendStep.class, EXTEND),
            Map.entry(LifetapStep.class, LIFETAP),
            Map.entry(AirbornStep.class, AIRBORN),
            Map.entry(RemoteStep.class, REMOTE),
            Map.entry(UnveilStep.class, UNVEIL),
            Map.entry(AncientStep.class, ANCIENT),
            Map.entry(PhaseStep.class, PHASE),
            Map.entry(SculkerStep.class, SCULKER),
            Map.entry(LeaderStep.class, LEADER));

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(LaidState::withDescendants));
    }
}
