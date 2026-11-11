package com.mercuriusxeno.goo.ability.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * The rally a peaceful mob holds until its rallier strikes it: the caster it
 * fights for, the damage each of its strikes deals, and the player who
 * attacked the caster, whom the mob fights first. Saved with the mob and
 * synced to every client drawing it.
 * zoo-rally-arms-the-peaceful
 * rally-is-a-mob-effect-on-one-target
 * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
 *
 * @param caster   the entity the mob fights for
 * @param damage   the damage one strike deals
 * @param attacker the player who attacked the caster, empty for none
 */
public record Rallied(UUID caster, float damage, Optional<UUID> attacker) {

    /** No rally: the mob fights for no one. */
    public static final Rallied NONE = new Rallied(new UUID(0L, 0L), 0f);

    private static final String FIELD_CASTER = "caster";
    private static final String FIELD_DAMAGE = "damage";
    private static final String FIELD_ATTACKER = "attacker";

    /** Saves the rally with the mob; a save's older expires_at field is ignored on load. */
    public static final MapCodec<Rallied> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf(FIELD_CASTER).forGetter(Rallied::caster),
            Codec.FLOAT.fieldOf(FIELD_DAMAGE).forGetter(Rallied::damage),
            UUIDUtil.CODEC.optionalFieldOf(FIELD_ATTACKER).forGetter(Rallied::attacker)
    ).apply(inst, Rallied::new));

    /**
     * Syncs the rally to the clients drawing the mob, which float the fist over its head and outline it.
     * rallied-mobs-wear-a-red-clenched-fist
     */
    public static final StreamCodec<ByteBuf, Rallied> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Rallied::caster,
            ByteBufCodecs.FLOAT, Rallied::damage,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Rallied::attacker,
            Rallied::new);

    /**
     * A rally recording no attacker.
     *
     * @param caster the entity the mob fights for
     * @param damage the damage one strike deals
     */
    public Rallied(UUID caster, float damage) {
        this(caster, damage, Optional.empty());
    }

    /**
     * Whether a hit from an attacker breaks the rally: only the rallier's does.
     * rally-is-a-mob-effect-on-one-target
     *
     * @param attacker the uuid of the entity behind the damage, or null for none
     * @return true when the rallier dealt the damage
     */
    public boolean brokenBy(@Nullable UUID attacker) {
        return caster.equals(attacker);
    }

    /**
     * This rally with a player recorded as the caster's attacker.
     * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
     *
     * @param player the player who attacked the caster
     * @return the rally recording the attacker
     */
    public Rallied withAttacker(UUID player) {
        return new Rallied(caster, damage, Optional.of(player));
    }

    /**
     * This rally with no attacker recorded, once the attacker died or left reach.
     * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
     *
     * @return the rally recording no attacker
     */
    public Rallied withoutAttacker() {
        return new Rallied(caster, damage);
    }

    /**
     * The attacker a second rally on a mob carries: the standing rally's, and
     * none where the mob holds no rally.
     * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
     *
     * @param standing the rally the mob holds, null for none
     * @return the attacker, empty for none
     */
    public static Optional<UUID> attackerAfter(@Nullable Rallied standing) {
        return standing != null ? standing.attacker() : Optional.empty();
    }

    /**
     * Whom a mob fights for once a rally reaches it: the first rallier while
     * that rally stands, the caller where the mob holds no rally.
     * rally-remembers-the-first-rallier
     *
     * @param standing the rally the mob holds, null for none
     * @param caller   the caster whose rally reaches the mob
     * @return the rallier's UUID
     */
    public static UUID rallierAfter(@Nullable Rallied standing, UUID caller) {
        return standing != null ? standing.caster() : caller;
    }
}
