package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * The self + brew effects a player holds. A glove effect stands from the
 * eat that started it until the player invokes it again or the inventory can
 * no longer pay its upkeep, with no timer; a drunk brew's effect is prepaid,
 * paying nothing until its expiry ends it. A heart-changing effect starting
 * ends every other heart-changing effect, its upkeep stopping with it. Times
 * are absolute game times; the record reads no level, so a unit test drives
 * it whole and the server tick subscriber applies what it answers.
 * self-effects-trickle-until-ended
 * brew-runs-the-crawl-prepaid-on-a-shown-clock
 *
 * @param held the effects standing, in the order they started
 */
public record HeldEffects(List<Held> held) {

    /** A player holding no effect. */
    public static final HeldEffects NONE = new HeldEffects(List.of());
    /** The expiry of a glove effect, which only its upkeep or the player ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;
    /** An extender covers the other effects' upkeep on one tick of every this many. */
    private static final int COVERED_EVERY = 2;

    private static final String FIELD_HELD = "held";

    /** Codec for the saved effects. */
    public static final MapCodec<HeldEffects> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Held.CODEC.listOf().fieldOf(FIELD_HELD).forGetter(HeldEffects::held)
    ).apply(inst, HeldEffects::new));

    /** Codec for the effects synced to the owning client. */
    public static final StreamCodec<ByteBuf, HeldEffects> STREAM_CODEC = StreamCodec.composite(
            Held.STREAM_CODEC.apply(ByteBufCodecs.list()), HeldEffects::held,
            HeldEffects::new);

    /**
     * Copies the list so the record holds it unmodifiable.
     */
    public HeldEffects {
        held = List.copyOf(held);
    }

    /**
     * One effect standing on the player.
     *
     * @param ability       the ability held
     * @param gooType       the goo type its upkeep draws
     * @param upkeep        the mB it pays each tick
     * @param lays          the player state its program laid, which ending it clears
     * @param startedAt     the game time it started at, a tick it pays nothing on
     * @param expiresAt     the game time a prepaid brew ends at, NEVER_EXPIRES for a glove effect
     * @param downSound     the cue the effect plays when it ends (decision held-effects-sound-up-and-down)
     */
    public record Held(Identifier ability, ResourceKey<GooTypeDefinition> gooType, int upkeep,
                       Set<LaidState> lays, long startedAt, long expiresAt, SoundCue downSound) {

        /**
         * The cue an effect whose ability names no down sound plays when it ends.
         * held-effects-sound-up-and-down
         */
        public static final SoundCue SHARED_DOWN_SOUND = SoundCue.of(GooSoundIds.ABILITY_DOWN);

        private static final String FIELD_ABILITY = "ability";
        private static final String FIELD_GOO_TYPE = "goo_type";
        private static final String FIELD_UPKEEP = "upkeep";
        private static final String FIELD_LAYS = "lays";
        private static final String FIELD_STARTED_AT = "started_at";
        private static final String FIELD_EXPIRES_AT = "expires_at";
        private static final String FIELD_DOWN_SOUND = "down_sound";

        static final Codec<Held> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Identifier.CODEC.fieldOf(FIELD_ABILITY).forGetter(Held::ability),
                GooTypes.KEY_CODEC.fieldOf(FIELD_GOO_TYPE).forGetter(Held::gooType),
                Codec.INT.fieldOf(FIELD_UPKEEP).forGetter(Held::upkeep),
                LaidState.CODEC.listOf().xmap(Held::laidSet, List::copyOf).fieldOf(FIELD_LAYS)
                        .forGetter(Held::lays),
                Codec.LONG.fieldOf(FIELD_STARTED_AT).forGetter(Held::startedAt),
                Codec.LONG.optionalFieldOf(FIELD_EXPIRES_AT, NEVER_EXPIRES).forGetter(Held::expiresAt),
                SoundCue.CODEC.optionalFieldOf(FIELD_DOWN_SOUND, SHARED_DOWN_SOUND).forGetter(Held::downSound)
        ).apply(inst, Held::new));

        static final StreamCodec<ByteBuf, Held> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Held::ability,
                GooTypes.KEY_STREAM_CODEC, Held::gooType,
                ByteBufCodecs.VAR_INT, Held::upkeep,
                LaidState.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Held::laidSet, List::copyOf), Held::lays,
                ByteBufCodecs.VAR_LONG, Held::startedAt,
                ByteBufCodecs.LONG, Held::expiresAt,
                SoundCue.STREAM_CODEC, Held::downSound,
                Held::new);

        /**
         * Copies the laid state so the record holds it unmodifiable.
         */
        public Held {
            lays = laidSet(lays);
        }

        /**
         * A glove effect, paying its upkeep with no expiry.
         *
         * @param ability   the ability held
         * @param gooType   the goo type its upkeep draws
         * @param upkeep    the mB it pays each tick
         * @param lays      the player state its program laid
         * @param startedAt the game time it started at
         */
        public Held(Identifier ability, ResourceKey<GooTypeDefinition> gooType, int upkeep, Set<LaidState> lays,
                    long startedAt) {
            this(ability, gooType, upkeep, lays, startedAt, NEVER_EXPIRES, SHARED_DOWN_SOUND);
        }

        /**
         * An effect playing the shared ability-down cue when it ends.
         *
         * @param ability   the ability held
         * @param gooType   the goo type its upkeep draws
         * @param upkeep    the mB it pays each tick
         * @param lays      the player state its program laid
         * @param startedAt the game time it started at
         * @param expiresAt the game time a prepaid brew ends at, NEVER_EXPIRES for a glove effect
         */
        public Held(Identifier ability, ResourceKey<GooTypeDefinition> gooType, int upkeep, Set<LaidState> lays,
                    long startedAt, long expiresAt) {
            this(ability, gooType, upkeep, lays, startedAt, expiresAt, SHARED_DOWN_SOUND);
        }

        /**
         * Answers whether the effect is a drunk brew's, prepaid until its expiry.
         * brew-runs-the-crawl-prepaid-on-a-shown-clock
         *
         * @return true for a prepaid effect
         */
        public boolean prepaid() {
            return expiresAt != NEVER_EXPIRES;
        }

        /**
         * Answers whether the effect lays a heart overlay, which
         * one-heart-overlay-at-a-time limits to one.
         *
         * @return true for a heart-changing effect
         */
        public boolean changesHearts() {
            return lays.contains(LaidState.HEART_OVERLAY);
        }

        /**
         * Answers whether the effect is the glove's Extender, whose paid upkeep
         * covers alternate ticks of every other glove effect's
         * (decision extender-multiplies-the-next-self-duration).
         *
         * @return true for a glove-held extender
         */
        public boolean extendsOthers() {
            return lays.contains(LaidState.EXTENDER) && !prepaid();
        }

        private static Set<LaidState> laidSet(Collection<LaidState> laid) {
            return laid.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(laid));
        }
    }

    /**
     * What a change leaves: the effects after it and the effects it ended.
     *
     * @param after the effects standing after the change
     * @param ended the effects the change ended, whose laid state the caller clears
     */
    public record Changed(HeldEffects after, List<Held> ended) {
    }

    /**
     * What a tick leaves: the effects after it, the effects it ended and the
     * upkeep it drew per goo type.
     *
     * @param after the effects standing after the tick
     * @param ended the effects that could not pay and ended
     * @param drawn per goo type, the mB the tick's upkeep draws from the inventory
     */
    public record Ticked(HeldEffects after, List<Held> ended, Map<ResourceKey<GooTypeDefinition>, Integer> drawn) {
    }

    /**
     * Answers whether an ability is held.
     *
     * @param ability the ability
     * @return true while it stands
     */
    public boolean holds(Identifier ability) {
        return find(ability).isPresent();
    }

    /**
     * Answers whether no effect is held.
     *
     * @return true for a player holding nothing
     */
    public boolean isEmpty() {
        return held.isEmpty();
    }

    /**
     * Starts an effect. A heart-changing effect ends every other
     * heart-changing effect; an effect already held is replaced.
     * one-heart-overlay-at-a-time
     *
     * @param started the effect starting
     * @return the effects after the start and those it ended
     */
    public Changed start(Held started) {
        List<Held> kept = new ArrayList<>();
        List<Held> ended = new ArrayList<>();
        for (Held standing : held) {
            if (standing.ability().equals(started.ability())) {
                continue;
            }
            if (started.changesHearts() && standing.changesHearts()) {
                ended.add(standing);
            } else {
                kept.add(standing);
            }
        }
        kept.add(started);
        return new Changed(new HeldEffects(kept), ended);
    }

    /**
     * Ends an effect deliberately.
     *
     * @param ability the ability to end
     * @return the effects after, and the effect ended, none when it was not held
     */
    public Changed end(Identifier ability) {
        Optional<Held> found = find(ability);
        if (found.isEmpty()) {
            return new Changed(this, List.of());
        }
        List<Held> kept = new ArrayList<>(held);
        kept.remove(found.get());
        return new Changed(new HeldEffects(kept), List.of(found.get()));
    }

    /**
     * Advances the effects one tick: a prepaid effect pays nothing and ends
     * at its expiry; each glove effect started before now pays its upkeep
     * from what its goo type holds, effects sharing a type drawing in the
     * order they started, and an effect the inventory cannot pay ends. A
     * glove extender pays first, and on the ticks its payment covers, every
     * other glove effect stands without paying
     * (decision extender-multiplies-the-next-self-duration).
     *
     * @param available the mB the inventory holds of a goo type
     * @param now       the game time
     * @return the effects after the tick, those ended and the upkeep drawn
     */
    public Ticked tick(ToIntFunction<ResourceKey<GooTypeDefinition>> available, long now) {
        if (held.isEmpty()) {
            return new Ticked(this, List.of(), Map.of());
        }
        Map<ResourceKey<GooTypeDefinition>, Integer> drawn = new HashMap<>();
        Set<Held> paidExtenders = payExtenders(available, drawn, now);
        boolean covered = paidExtenders.stream().anyMatch(extender -> creditsThisTick(extender, now));
        List<Held> kept = new ArrayList<>();
        List<Held> ended = new ArrayList<>();
        for (Held standing : held) {
            boolean stood = standing.extendsOthers() ? paidExtenders.contains(standing)
                    : standsBeside(standing, covered, available, drawn, now);
            (stood ? kept : ended).add(standing);
        }
        HeldEffects after = ended.isEmpty() ? this : new HeldEffects(kept);
        return new Ticked(after, List.copyOf(ended), Map.copyOf(drawn));
    }

    /**
     * Lengthens every prepaid brew standing, the extender's own aside, by a
     * flat count of ticks, as drinking the pulse brew does
     * (decision extender-multiplies-the-next-self-duration).
     *
     * @param extra the ticks each brew's expiry moves out
     * @return the effects after
     */
    public HeldEffects extendPrepaid(long extra) {
        return new HeldEffects(held.stream().map(standing -> standing.prepaid()
                && !standing.lays().contains(LaidState.EXTENDER)
                ? new Held(standing.ability(), standing.gooType(), standing.upkeep(), standing.lays(),
                        standing.startedAt(), standing.expiresAt() + extra, standing.downSound())
                : standing).toList());
    }

    /**
     * The ticks a drunk pulse brew adds to each timed effect applied while it
     * stands: its own duration, or none while no pulse brew stands
     * (decision extender-multiplies-the-next-self-duration).
     *
     * @return the ticks added, 0 while no drunk extender stands
     */
    public long extensionTicks() {
        return held.stream().filter(standing -> standing.prepaid() && standing.lays().contains(LaidState.EXTENDER))
                .mapToLong(standing -> standing.expiresAt() - standing.startedAt()).max().orElse(0);
    }

    /**
     * Answers whether an effect other than an extender stands through a
     * tick: a glove effect stands unpaid on a tick an extender covers, and
     * pays as usual otherwise.
     *
     * @param standing  the effect
     * @param covered   whether an extender's payment covers this tick
     * @param available the mB the inventory holds of a goo type
     * @param drawn     per goo type, the upkeep the tick has drawn so far
     * @param now       the game time
     * @return true when the effect stands after the tick
     */
    private static boolean standsBeside(Held standing, boolean covered,
                                        ToIntFunction<ResourceKey<GooTypeDefinition>> available,
                                        Map<ResourceKey<GooTypeDefinition>, Integer> drawn, long now) {
        return covered && !standing.prepaid() || stands(standing, available, drawn, now);
    }

    /**
     * Draws each glove extender's upkeep ahead of the other effects'.
     *
     * @param available the mB the inventory holds of a goo type
     * @param drawn     per goo type, the upkeep the tick has drawn so far
     * @param now       the game time
     * @return the extenders standing through the tick
     */
    private Set<Held> payExtenders(ToIntFunction<ResourceKey<GooTypeDefinition>> available,
                                   Map<ResourceKey<GooTypeDefinition>, Integer> drawn, long now) {
        Set<Held> paid = new HashSet<>();
        for (Held standing : held) {
            if (standing.extendsOthers() && stands(standing, available, drawn, now)) {
                paid.add(standing);
            }
        }
        return paid;
    }

    /**
     * Answers whether a standing extender's payment this tick covers the
     * other glove effects' upkeep: every second tick it pays, so each other
     * effect pays its own goo on alternate ticks
     * (decision extender-multiplies-the-next-self-duration).
     *
     * @param extender the extender, standing through the tick
     * @param now      the game time
     * @return true on a tick the extender's payment covers
     */
    static boolean creditsThisTick(Held extender, long now) {
        long since = now - extender.startedAt();
        return since > 0 && since % COVERED_EVERY == 0;
    }

    /**
     * Answers whether one effect stands through a tick, drawing its upkeep
     * into the tick's draws when it pays.
     *
     * @param standing  the effect
     * @param available the mB the inventory holds of a goo type
     * @param drawn     per goo type, the upkeep the tick has drawn so far
     * @param now       the game time
     * @return true when the effect stands after the tick
     */
    private static boolean stands(Held standing, ToIntFunction<ResourceKey<GooTypeDefinition>> available,
                                  Map<ResourceKey<GooTypeDefinition>, Integer> drawn, long now) {
        if (standing.prepaid()) {
            // brew-runs-the-crawl-prepaid-on-a-shown-clock: the brew paid up front and runs out on its clock
            return now < standing.expiresAt();
        }
        if (standing.startedAt() >= now) {
            return true;
        }
        int alreadyDrawn = drawn.getOrDefault(standing.gooType(), 0);
        if (available.applyAsInt(standing.gooType()) - alreadyDrawn < standing.upkeep()) {
            return false;
        }
        drawn.merge(standing.gooType(), standing.upkeep(), Integer::sum);
        return true;
    }

    /**
     * How long each goo type's holdings keep its glove effects paid: the mB
     * the inventory holds of the type over the upkeep its glove effects draw a
     * tick, which the effect list shows as the time left. A prepaid brew has
     * a clock of its own and counts no upkeep here. While a glove extender
     * stands, every other glove effect pays on alternate ticks alone, so its
     * upkeep counts half and its time shows doubled
     * (decision extender-multiplies-the-next-self-duration).
     * brew-runs-the-crawl-prepaid-on-a-shown-clock
     *
     * @param available the mB the inventory holds of a goo type
     * @return per goo type holding a glove effect, the ticks its holdings pay for
     */
    public Map<ResourceKey<GooTypeDefinition>, Integer> ticksLeft(
            ToIntFunction<ResourceKey<GooTypeDefinition>> available) {
        boolean extended = held.stream().anyMatch(Held::extendsOthers);
        Map<ResourceKey<GooTypeDefinition>, Double> upkeep = new HashMap<>();
        for (Held standing : held) {
            if (!standing.prepaid() && standing.upkeep() > 0) {
                double paid = extended && !standing.extendsOthers()
                        ? (double) standing.upkeep() / COVERED_EVERY : standing.upkeep();
                upkeep.merge(standing.gooType(), paid, Double::sum);
            }
        }
        Map<ResourceKey<GooTypeDefinition>, Integer> left = new HashMap<>();
        upkeep.forEach((type, perTick) -> left.put(type, (int) (available.applyAsInt(type) / perTick)));
        return Map.copyOf(left);
    }

    /**
     * Plays each ended effect's down cue once, however it ended: by the
     * player's press, by running dry, by a heart effect replacing it or by a
     * brew's expiry.
     * held-effects-sound-up-and-down
     *
     * @param ended the effects a change ended
     * @param sink  where a cue plays, the player's host in the game
     */
    public static void soundEnds(List<Held> ended, Consumer<SoundCue> sink) {
        for (Held effect : ended) {
            sink.accept(effect.downSound());
        }
    }

    private Optional<Held> find(Identifier ability) {
        return held.stream().filter(standing -> standing.ability().equals(ability)).findFirst();
    }
}
