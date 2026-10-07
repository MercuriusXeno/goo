package com.mercuriusxeno.goo.ability.held;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * The self + brew effects a player holds on the glove. Each stands from the
 * eat that started it until the player invokes it again or the inventory can
 * no longer pay its upkeep, with no timer. A heart-changing effect starting
 * ends every other heart-changing effect, its upkeep stopping with it. Times
 * are absolute game times; the record reads no level, so a unit test drives
 * it whole and the server tick subscriber applies what it answers.
 * self-effects-trickle-until-ended
 *
 * @param held the effects standing, in the order they started
 */
public record HeldEffects(List<Held> held) {

    /** A player holding no effect. */
    public static final HeldEffects NONE = new HeldEffects(List.of());

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
     * One effect standing on the glove.
     *
     * @param ability       the ability held
     * @param gooType       the goo type its upkeep draws
     * @param upkeep        the mB it pays each tick
     * @param lays          the player state its program laid, which ending it clears
     * @param startedAt     the game time it started at, a tick it pays nothing on
     */
    public record Held(Identifier ability, ResourceKey<GooTypeDefinition> gooType, int upkeep,
                       Set<LaidState> lays, long startedAt) {

        private static final String FIELD_ABILITY = "ability";
        private static final String FIELD_GOO_TYPE = "goo_type";
        private static final String FIELD_UPKEEP = "upkeep";
        private static final String FIELD_LAYS = "lays";
        private static final String FIELD_STARTED_AT = "started_at";

        static final Codec<Held> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Identifier.CODEC.fieldOf(FIELD_ABILITY).forGetter(Held::ability),
                GooTypes.KEY_CODEC.fieldOf(FIELD_GOO_TYPE).forGetter(Held::gooType),
                Codec.INT.fieldOf(FIELD_UPKEEP).forGetter(Held::upkeep),
                LaidState.CODEC.listOf().xmap(Held::laidSet, List::copyOf).fieldOf(FIELD_LAYS)
                        .forGetter(Held::lays),
                Codec.LONG.fieldOf(FIELD_STARTED_AT).forGetter(Held::startedAt)
        ).apply(inst, Held::new));

        static final StreamCodec<ByteBuf, Held> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Held::ability,
                GooTypes.KEY_STREAM_CODEC, Held::gooType,
                ByteBufCodecs.VAR_INT, Held::upkeep,
                LaidState.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Held::laidSet, List::copyOf), Held::lays,
                ByteBufCodecs.VAR_LONG, Held::startedAt,
                Held::new);

        /**
         * Copies the laid state so the record holds it unmodifiable.
         */
        public Held {
            lays = laidSet(lays);
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
     * Advances the effects one tick: each effect started before now pays its
     * upkeep from what its goo type holds, effects sharing a type drawing in
     * the order they started, and an effect the inventory cannot pay ends.
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
        List<Held> kept = new ArrayList<>();
        List<Held> ended = new ArrayList<>();
        for (Held standing : held) {
            if (standing.startedAt() >= now) {
                kept.add(standing);
                continue;
            }
            int alreadyDrawn = drawn.getOrDefault(standing.gooType(), 0);
            if (available.applyAsInt(standing.gooType()) - alreadyDrawn < standing.upkeep()) {
                ended.add(standing);
            } else {
                drawn.merge(standing.gooType(), standing.upkeep(), Integer::sum);
                kept.add(standing);
            }
        }
        HeldEffects after = ended.isEmpty() ? this : new HeldEffects(kept);
        return new Ticked(after, List.copyOf(ended), Map.copyOf(drawn));
    }

    /**
     * Ends every heart-changing effect, as when the overlay they laid no
     * longer stands and nothing is left for their upkeep to hold.
     *
     * @return the effects after, and the heart-changing effects ended
     */
    public Changed endHeartChanging() {
        List<Held> ended = held.stream().filter(Held::changesHearts).toList();
        if (ended.isEmpty()) {
            return new Changed(this, List.of());
        }
        return new Changed(new HeldEffects(held.stream().filter(standing -> !standing.changesHearts()).toList()),
                ended);
    }

    private Optional<Held> find(Identifier ability) {
        return held.stream().filter(standing -> standing.ability().equals(ability)).findFirst();
    }
}
