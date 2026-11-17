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
 * The rally a peaceful mob holds until its rallier strikes it or casts Rally
 * on it again: the caster it
 * fights for, the damage each of its strikes deals, and the player who
 * attacked the caster, whom the mob fights first, and the armor it wears
 * from the caster. Saved with the mob and synced to every client drawing it.
 * zoo-rally-arms-the-peaceful
 * rally-is-a-mob-effect-on-one-target
 * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
 * rally-minions-get-half-your-armor-and-a-floor
 * slots-follow-rally-order-size-and-count
 *
 * @param caster   the entity the mob fights for
 * @param damage   the damage one strike deals
 * @param attacker the player who attacked the caster, empty for none
 * @param armor      the armor the mob wears from the caster
 * @param rallyOrder when the mob was first rallied, the level's game time, which sets its slot in the formation
 */
public record Rallied(UUID caster, float damage, Optional<UUID> attacker, RallyArmor armor, long rallyOrder) {

    /**
     * The rally order a save written before rallies carried one loads with:
     * ahead of every stamped rally, its ties broken by uuid.
     * slots-follow-rally-order-size-and-count
     */
    public static final long DEFAULT_RALLY_ORDER = 0L;

    /** No rally: the mob fights for no one. */
    public static final Rallied NONE = new Rallied(new UUID(0L, 0L), 0f);

    private static final String FIELD_CASTER = "caster";
    private static final String FIELD_DAMAGE = "damage";
    private static final String FIELD_ATTACKER = "attacker";
    private static final String FIELD_ARMOR = "armor";
    private static final String FIELD_RALLY_ORDER = "rally_order";

    /** Saves the rally with the mob; a save's older expires_at field is ignored on load. */
    public static final MapCodec<Rallied> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf(FIELD_CASTER).forGetter(Rallied::caster),
            Codec.FLOAT.fieldOf(FIELD_DAMAGE).forGetter(Rallied::damage),
            UUIDUtil.CODEC.optionalFieldOf(FIELD_ATTACKER).forGetter(Rallied::attacker),
            RallyArmor.CODEC.optionalFieldOf(FIELD_ARMOR, RallyArmor.NONE).forGetter(Rallied::armor),
            Codec.LONG.optionalFieldOf(FIELD_RALLY_ORDER, DEFAULT_RALLY_ORDER).forGetter(Rallied::rallyOrder)
    ).apply(inst, Rallied::new));

    /**
     * Syncs the rally to the clients drawing the mob, which float the fist over its head and outline it.
     * rallied-mobs-wear-a-red-clenched-fist
     */
    public static final StreamCodec<ByteBuf, Rallied> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Rallied::caster,
            ByteBufCodecs.FLOAT, Rallied::damage,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Rallied::attacker,
            RallyArmor.STREAM_CODEC, Rallied::armor,
            ByteBufCodecs.VAR_LONG, Rallied::rallyOrder,
            Rallied::new);

    /**
     * A rally recording no attacker, lending no armor and standing at the default rally order.
     *
     * @param caster the entity the mob fights for
     * @param damage the damage one strike deals
     */
    public Rallied(UUID caster, float damage) {
        this(caster, damage, Optional.empty(), RallyArmor.NONE, DEFAULT_RALLY_ORDER);
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
     * Whether a Rally cast by a caller releases this rally: only its rallier's
     * does, and a mob holding no rally has none to release.
     * rally-cast-on-your-rallied-mob-releases-it
     *
     * @param caller the caster whose Rally reaches the mob
     * @return true when the caller is the rally's rallier
     */
    public boolean releasedBy(UUID caller) {
        return !NONE.equals(this) && caster.equals(caller);
    }

    /**
     * This rally with a player recorded as the caster's attacker.
     * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
     *
     * @param player the player who attacked the caster
     * @return the rally recording the attacker
     */
    public Rallied withAttacker(UUID player) {
        return new Rallied(caster, damage, Optional.of(player), armor, rallyOrder);
    }

    /**
     * This rally with no attacker recorded, once the attacker died or left reach.
     * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
     *
     * @return the rally recording no attacker
     */
    public Rallied withoutAttacker() {
        return new Rallied(caster, damage, Optional.empty(), armor, rallyOrder);
    }
}
