package com.mercuriusxeno.goo.ability.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/**
 * A rallied mob fallen downed instead of dying: the game time it went down,
 * whether its AI was off before the fall, so a raise puts the AI back as it
 * stood, and the negative health its killing blow left it, which heals count
 * up from until it passes 0, and the game time its last heal floored its
 * death at. For as long as it is down it shrugs off attacks and falls; past
 * its death span, 20 seconds or the 10 a heal leaves, it dies. Saved with the mob and synced to
 * every client drawing it, which lays it on its side.
 * rallied-mob-falls-downed-instead-of-dying
 * downed-mob-revives-only-healed-past-zero
 *
 * @param downedAt       the game time the mob went down
 * @param wasNoAi        whether the mob's AI was off before it went down
 * @param negativeHealth    the health below zero the mob holds, at or below 0 while it lies down
 * @param diesNoSoonerThan  the game time its last heal floored its death at, 0 for a fall never healed
 */
public record Downed(long downedAt, boolean wasNoAi, float negativeHealth, long diesNoSoonerThan) {

    /** No fall: the mob stands. */
    public static final Downed NONE = new Downed(0L, false, 0f, 0L);

    /**
     * Ticks after going down that a mob still downed dies, 20 seconds.
     * downed-mob-dies-at-twenty-seconds-and-a-heal-floors-ten
     */
    public static final int DEATH_TICKS = 400;
    /**
     * The ticks a heal on a downed mob leaves it at the least, 10 seconds.
     * downed-mob-dies-at-twenty-seconds-and-a-heal-floors-ten
     */
    public static final int HEAL_FLOOR_TICKS = 200;

    private static final String FIELD_DOWNED_AT = "downed_at";
    private static final String FIELD_WAS_NO_AI = "was_no_ai";
    private static final String FIELD_NEGATIVE_HEALTH = "negative_health";
    private static final String FIELD_DIES_NO_SOONER_THAN = "dies_no_sooner_than";

    /** Saves the fall with the mob, so a reload keeps its timers and its negative health. */
    public static final MapCodec<Downed> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_DOWNED_AT).forGetter(Downed::downedAt),
            Codec.BOOL.fieldOf(FIELD_WAS_NO_AI).forGetter(Downed::wasNoAi),
            Codec.FLOAT.optionalFieldOf(FIELD_NEGATIVE_HEALTH, 0f).forGetter(Downed::negativeHealth),
            Codec.LONG.optionalFieldOf(FIELD_DIES_NO_SOONER_THAN, 0L).forGetter(Downed::diesNoSoonerThan)
    ).apply(inst, Downed::new));

    /**
     * Syncs the fall to the clients drawing the mob, which lay it on its side
     * and draw its negative health.
     */
    public static final StreamCodec<ByteBuf, Downed> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Downed::downedAt,
            ByteBufCodecs.BOOL, Downed::wasNoAi,
            ByteBufCodecs.FLOAT, Downed::negativeHealth,
            ByteBufCodecs.VAR_LONG, Downed::diesNoSoonerThan,
            Downed::new);

    /**
     * This fall with a heal's amount added to its negative health.
     * downed-mob-revives-only-healed-past-zero
     *
     * @param amount the health the heal restores
     * @return the fall after the heal
     */
    public Downed healedBy(float amount) {
        return new Downed(downedAt, wasNoAi, negativeHealth + amount, diesNoSoonerThan);
    }

    /**
     * Whether heals have carried the negative health past 0, so the mob stands.
     * downed-mob-revives-only-healed-past-zero
     *
     * @return true once the count is above 0
     */
    public boolean revives() {
        return negativeHealth > 0f;
    }

    /**
     * This fall with a heal landed on it at a game time: the time the mob
     * has left floored at 10 seconds, so with less left it has 10 and with
     * more its death time stands, and repeated heals never stack time past
     * the floor.
     * downed-mob-dies-at-twenty-seconds-and-a-heal-floors-ten
     *
     * @param gameTime the game time the heal lands
     * @return the fall after the heal
     */
    public Downed flooredByHealAt(long gameTime) {
        return new Downed(downedAt, wasNoAi, negativeHealth,
                Math.max(diesNoSoonerThan, gameTime + HEAL_FLOOR_TICKS));
    }

    /**
     * The game time the mob, still downed, dies at: 20 seconds after going
     * down, or later where a heal floored it.
     * downed-mob-dies-at-twenty-seconds-and-a-heal-floors-ten
     *
     * @return the game time it dies
     */
    public long deathAt() {
        return Math.max(downedAt + DEATH_TICKS, diesNoSoonerThan);
    }

    /**
     * Whether the mob, still downed at a game time, has outlived its death span.
     *
     * @param gameTime the game time
     * @return true once the death span has passed
     */
    public boolean diesAt(long gameTime) {
        return gameTime >= deathAt();
    }

    /**
     * Whether a hit is an attack: damage some entity caused or carried, or an explosion.
     *
     * @param source the hit's source
     * @return true for an attack
     */
    public static boolean isAttack(DamageSource source) {
        return source.getEntity() != null || source.getDirectEntity() != null
                || source.is(DamageTypeTags.IS_EXPLOSION);
    }

    /**
     * Whether a lethal hit on a rallied mob downs it rather than killing it:
     * an attack does, and the world's harm, lava, fire, the void, drowning
     * and the like, kills outright.
     *
     * @param source the lethal hit's source
     * @return true where the mob goes downed
     */
    public static boolean downs(DamageSource source) {
        return isAttack(source);
    }

    /**
     * Whether a downed mob shrugs off a hit: an attack or a fall, at any
     * time down, a fall since the blow's knockback or a blast taking out the
     * floor often throws it; the world's other harm lands.
     * no-attack-lands-on-a-downed-mob
     * rallied-mob-falls-downed-instead-of-dying
     *
     * @param source the hit's source
     * @return true where the hit is negated
     */
    public static boolean negates(DamageSource source) {
        return isAttack(source) || source.is(DamageTypeTags.IS_FALL);
    }
}
