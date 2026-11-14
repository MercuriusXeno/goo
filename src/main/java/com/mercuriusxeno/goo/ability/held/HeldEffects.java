package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.datafixers.util.Either;
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
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The durative effects a player holds. An effect stands from the cast that
 * started it until the player invokes it again or the inventory can no longer
 * pay its upkeep, with no timer. A heart-changing effect starting ends every
 * other heart-changing effect, its upkeep stopping with it. Times are absolute
 * game times; the record reads no level, so a unit test drives it whole and
 * the server tick subscriber applies what it answers.
 * self-effects-trickle-until-ended
 *
 * @param held the effects standing, in the order they started
 */
public record HeldEffects(List<Held> held) {

    /** A player holding no effect. */
    public static final HeldEffects NONE = new HeldEffects(List.of());
    /** An Extend covers the other effects' upkeep on one tick of every this many. */
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
     * @param downSound     the cue the effect plays when it ends (decision held-effects-sound-up-and-down)
     */
    public record Held(Identifier ability, ResourceKey<GooTypeDefinition> gooType, GooFraction upkeep,
                       Set<LaidState> lays, long startedAt, SoundCue downSound) {

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
        private static final String FIELD_DOWN_SOUND = "down_sound";


        // A whole upkeep saves as the number it saved as before it took fractions.
        private static final Codec<GooFraction> UPKEEP_CODEC = Codec.either(Codec.INT, GooFraction.CODEC).xmap(
                saved -> saved.map(GooFraction::of, Function.identity()),
                upkeep -> upkeep.isWhole() ? Either.left((int) upkeep.numerator()) : Either.right(upkeep));

        static final Codec<Held> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Identifier.CODEC.fieldOf(FIELD_ABILITY).forGetter(Held::ability),
                GooTypes.KEY_CODEC.fieldOf(FIELD_GOO_TYPE).forGetter(Held::gooType),
                UPKEEP_CODEC.fieldOf(FIELD_UPKEEP).forGetter(Held::upkeep),
                LaidState.CODEC.listOf().xmap(Held::laidSet, List::copyOf).fieldOf(FIELD_LAYS)
                        .forGetter(Held::lays),
                Codec.LONG.fieldOf(FIELD_STARTED_AT).forGetter(Held::startedAt),
                SoundCue.CODEC.optionalFieldOf(FIELD_DOWN_SOUND, SHARED_DOWN_SOUND).forGetter(Held::downSound)
        ).apply(inst, Held::new));

        static final StreamCodec<ByteBuf, Held> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Held::ability,
                GooTypes.KEY_STREAM_CODEC, Held::gooType,
                GooFraction.STREAM_CODEC, Held::upkeep,
                LaidState.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Held::laidSet, List::copyOf), Held::lays,
                ByteBufCodecs.VAR_LONG, Held::startedAt,
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
         * @param upkeep    the exact mB it pays each tick
         * @param lays      the player state its program laid
         * @param startedAt the game time it started at
         */
        public Held(Identifier ability, ResourceKey<GooTypeDefinition> gooType, GooFraction upkeep,
                    Set<LaidState> lays, long startedAt) {
            this(ability, gooType, upkeep, lays, startedAt, SHARED_DOWN_SOUND);
        }

        /**
         * A glove effect paying a whole upkeep, with no expiry.
         *
         * @param ability   the ability held
         * @param gooType   the goo type its upkeep draws
         * @param upkeep    the whole mB it pays each tick
         * @param lays      the player state its program laid
         * @param startedAt the game time it started at
         */
        public Held(Identifier ability, ResourceKey<GooTypeDefinition> gooType, int upkeep, Set<LaidState> lays,
                    long startedAt) {
            this(ability, gooType, GooFraction.of(upkeep), lays, startedAt);
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
         * Answers whether the effect is the Extend, whose paid upkeep covers
         * alternate ticks of every other held effect's
         * (decision extender-multiplies-the-next-self-duration).
         *
         * @return true for the Extend
         */
        public boolean extendsOthers() {
            return lays.contains(LaidState.EXTEND);
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
     * @param drawn      per goo type, the whole mB the tick's upkeep draws from the inventory
     * @param remainders the player's remainders after the tick's upkeep paid through them
     */
    public record Ticked(HeldEffects after, List<Held> ended, Map<ResourceKey<GooTypeDefinition>, Integer> drawn,
                         GooRemainders remainders) {
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
     * Ends every effect whose ability is no longer known, as a save from
     * before an ability's rename or removal carries: such an effect lays
     * nothing and grants nothing, so it ends rather than standing on.
     * dive-is-renamed-sculker
     *
     * @param known whether an ability is still known
     * @return the effects after, and those ended
     */
    public Changed endUnknown(Predicate<Identifier> known) {
        List<Held> kept = new ArrayList<>();
        List<Held> ended = new ArrayList<>();
        for (Held standing : held) {
            (known.test(standing.ability()) ? kept : ended).add(standing);
        }
        return ended.isEmpty() ? new Changed(this, List.of()) : new Changed(new HeldEffects(kept), ended);
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
     * Advances the effects one tick: each effect started before now pays its
     * upkeep from what its goo type holds, effects sharing a type drawing in
     * the order they started, and an effect the inventory cannot pay ends. An
     * Extend pays first, and on the ticks its payment covers, every other
     * effect stands without paying
     * (decision extender-multiplies-the-next-self-duration).
     *
     * @param available the mB the inventory holds of a goo type
     * @param now       the game time
     * @return the effects after the tick, those ended and the upkeep drawn
     */
    public Ticked tick(ToIntFunction<ResourceKey<GooTypeDefinition>> available, long now) {
        return tick(available, GooRemainders.EMPTY, now);
    }

    /**
     * Advances the effects one tick as {@link #tick(ToIntFunction, long)} does, each upkeep paying through the
     * player's remainders first.
     *
     * @param available  the mB the inventory holds of a goo type
     * @param remainders the player's remainders
     * @param now        the game time
     * @return the effects after the tick, those ended, the whole upkeep drawn and the remainders after
     */
    public Ticked tick(ToIntFunction<ResourceKey<GooTypeDefinition>> available, GooRemainders remainders, long now) {
        if (held.isEmpty()) {
            return new Ticked(this, List.of(), Map.of(), remainders);
        }
        TickPurse purse = new TickPurse(available, new HashMap<>(), new RemainderLedger(remainders), now);
        Set<Held> paidExtends = payExtends(purse);
        boolean covered = paidExtends.stream().anyMatch(extend -> creditsThisTick(extend, now));
        List<Held> kept = new ArrayList<>();
        List<Held> ended = new ArrayList<>();
        for (Held standing : held) {
            boolean stood = standing.extendsOthers() ? paidExtends.contains(standing)
                    : standsBeside(standing, covered, purse);
            (stood ? kept : ended).add(standing);
        }
        HeldEffects after = ended.isEmpty() ? this : new HeldEffects(kept);
        return new Ticked(after, List.copyOf(ended), Map.copyOf(purse.drawn()), purse.ledger().remainders());
    }


    /**
     * Answers whether an effect other than an Extend stands through a
     * tick: an effect stands unpaid on a tick an Extend covers, and
     * pays as usual otherwise.
     *
     * @param standing the effect
     * @param covered  whether an Extend's payment covers this tick
     * @param purse    what the tick pays from
     * @return true when the effect stands after the tick
     */
    private static boolean standsBeside(Held standing, boolean covered, TickPurse purse) {
        return covered || stands(standing, purse);
    }

    /**
     * Draws each Extend's upkeep ahead of the other effects'.
     *
     * @param purse what the tick pays from
     * @return the Extends standing through the tick
     */
    private Set<Held> payExtends(TickPurse purse) {
        Set<Held> paid = new HashSet<>();
        for (Held standing : held) {
            if (standing.extendsOthers() && stands(standing, purse)) {
                paid.add(standing);
            }
        }
        return paid;
    }

    /**
     * Answers whether a standing Extend's payment this tick covers the
     * other effects' upkeep: every second tick it pays, so each other
     * effect pays its own goo on alternate ticks
     * (decision extender-multiplies-the-next-self-duration).
     *
     * @param extend the Extend, standing through the tick
     * @param now      the game time
     * @return true on a tick the Extend's payment covers
     */
    static boolean creditsThisTick(Held extend, long now) {
        long since = now - extend.startedAt();
        return since > 0 && since % COVERED_EVERY == 0;
    }

    /**
     * Answers whether one effect stands through a tick, drawing its upkeep
     * into the tick's draws when it pays.
     *
     * @param standing the effect
     * @param purse    what the tick pays from
     * @return true when the effect stands after the tick
     */
    private static boolean stands(Held standing, TickPurse purse) {
        return standing.startedAt() >= purse.now() || purse.pays(standing.gooType(), standing.upkeep());
    }

    /**
     * What one tick's upkeep pays from: the inventory's holdings, the whole mB the tick has drawn so far and the
     * player's remainders, which pay first.
     * decision goo-is-an-int-and-only-the-remainder-is-a-fraction
     *
     * @param available the mB the inventory holds of a goo type
     * @param drawn     per goo type, the whole mB the tick has drawn so far
     * @param ledger    the player's remainders through the tick
     * @param now       the game time
     */
    private record TickPurse(ToIntFunction<ResourceKey<GooTypeDefinition>> available,
                             Map<ResourceKey<GooTypeDefinition>, Integer> drawn, RemainderLedger ledger, long now) {

        // Draws an exact upkeep, the remainder paying first; false where the holdings left cannot cover the rest.
        boolean pays(ResourceKey<GooTypeDefinition> type, GooFraction upkeep) {
            OptionalInt whole = ledger.debitExact(type, upkeep,
                    available.applyAsInt(type) - drawn.getOrDefault(type, 0));
            whole.ifPresent(mb -> drawn.merge(type, mb, Integer::sum));
            return whole.isPresent();
        }
    }

    /**
     * How long each goo type's holdings keep its effects paid: the mB the
     * inventory holds of the type over the upkeep its effects draw a tick,
     * which the effect list shows as the time left. While an Extend stands,
     * every other effect pays on alternate ticks alone, so its upkeep counts
     * half and its time shows doubled
     * (decision extender-multiplies-the-next-self-duration).
     *
     * @param available the mB the inventory holds of a goo type
     * @return per goo type holding an effect, the ticks its holdings pay for
     */
    public Map<ResourceKey<GooTypeDefinition>, Integer> ticksLeft(
            ToIntFunction<ResourceKey<GooTypeDefinition>> available) {
        Map<ResourceKey<GooTypeDefinition>, Integer> left = new HashMap<>();
        upkeepPerTick().forEach((type, perTick) -> left.put(type, (int) (available.applyAsInt(type) / perTick)));
        return Map.copyOf(left);
    }

    /**
     * The mB a tick each goo type's effects draw, the rate ticksLeft divides by: while an
     * Extend stands every other effect's upkeep counts half
     * (decision extender-multiplies-the-next-self-duration).
     * time-counts-net-standing-upkeep
     *
     * @return per goo type holding a paying effect, its upkeep a tick
     */
    public Map<ResourceKey<GooTypeDefinition>, Double> upkeepPerTick() {
        boolean extended = extended();
        Map<ResourceKey<GooTypeDefinition>, Double> upkeep = new HashMap<>();
        for (Held standing : held) {
            if (standing.upkeep().isPositive()) {
                double paid = extended && !standing.extendsOthers()
                        ? standing.upkeep().toDouble() / COVERED_EVERY : standing.upkeep().toDouble();
                upkeep.merge(standing.gooType(), paid, Double::sum);
            }
        }
        return Map.copyOf(upkeep);
    }

    /**
     * Whether an Extend stands, halving every other effect's upkeep.
     *
     * @return true while one stands
     */
    public boolean extended() {
        return held.stream().anyMatch(Held::extendsOthers);
    }

    /**
     * The share of its upkeep an effect pays while an Extend stands.
     *
     * @return the divisor an extended upkeep counts under
     */
    public static int extendedDivisor() {
        return COVERED_EVERY;
    }

    /**
     * Plays each ended effect's down cue once, however it ended: by the
     * player's press, by running dry or by a heart effect replacing it.
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
