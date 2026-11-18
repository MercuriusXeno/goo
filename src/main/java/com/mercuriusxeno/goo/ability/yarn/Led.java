package com.mercuriusxeno.goo.ability.yarn;

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
 * The lead a peaceful mob holds until its leader strikes it or casts Lead
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
 * @param leadOrder when the mob was first led, the level's game time, which sets its slot in the formation
 */
public record Led(UUID caster, float damage, Optional<UUID> attacker, LeadArmor armor, long leadOrder) {

    /**
     * The lead order a save written before leads carried one loads with:
     * ahead of every stamped lead, its ties broken by uuid.
     * slots-follow-rally-order-size-and-count
     */
    public static final long DEFAULT_LEAD_ORDER = 0L;

    /** No lead: the mob fights for no one. */
    public static final Led NONE = new Led(new UUID(0L, 0L), 0f);

    private static final String FIELD_CASTER = "caster";
    private static final String FIELD_DAMAGE = "damage";
    private static final String FIELD_ATTACKER = "attacker";
    private static final String FIELD_ARMOR = "armor";
    private static final String FIELD_LEAD_ORDER = "lead_order";

    /** Saves the lead with the mob; a save's older expires_at field is ignored on load. */
    public static final MapCodec<Led> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf(FIELD_CASTER).forGetter(Led::caster),
            Codec.FLOAT.fieldOf(FIELD_DAMAGE).forGetter(Led::damage),
            UUIDUtil.CODEC.optionalFieldOf(FIELD_ATTACKER).forGetter(Led::attacker),
            LeadArmor.CODEC.optionalFieldOf(FIELD_ARMOR, LeadArmor.NONE).forGetter(Led::armor),
            Codec.LONG.optionalFieldOf(FIELD_LEAD_ORDER, DEFAULT_LEAD_ORDER).forGetter(Led::leadOrder)
    ).apply(inst, Led::new));

    /**
     * Syncs the lead to the clients drawing the mob, which float the fist over its head and outline it.
     * rallied-mobs-wear-a-red-clenched-fist
     */
    public static final StreamCodec<ByteBuf, Led> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Led::caster,
            ByteBufCodecs.FLOAT, Led::damage,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Led::attacker,
            LeadArmor.STREAM_CODEC, Led::armor,
            ByteBufCodecs.VAR_LONG, Led::leadOrder,
            Led::new);

    /**
     * A lead recording no attacker, lending no armor and standing at the default lead order.
     *
     * @param caster the entity the mob fights for
     * @param damage the damage one strike deals
     */
    public Led(UUID caster, float damage) {
        this(caster, damage, Optional.empty(), LeadArmor.NONE, DEFAULT_LEAD_ORDER);
    }

    /**
     * Whether a hit from an attacker breaks the lead: only the leader's does.
     * rally-is-a-mob-effect-on-one-target
     *
     * @param attacker the uuid of the entity behind the damage, or null for none
     * @return true when the leader dealt the damage
     */
    public boolean brokenBy(@Nullable UUID attacker) {
        return caster.equals(attacker);
    }

    /**
     * Whether a Lead cast by a caller releases this lead: only its leader's
     * does, and a mob holding no lead has none to release.
     * rally-cast-on-your-rallied-mob-releases-it
     *
     * @param caller the caster whose Lead reaches the mob
     * @return true when the caller is the lead's leader
     */
    public boolean releasedBy(UUID caller) {
        return !NONE.equals(this) && caster.equals(caller);
    }

    /**
     * This lead with a player recorded as the caster's attacker.
     * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
     *
     * @param player the player who attacked the caster
     * @return the lead recording the attacker
     */
    public Led withAttacker(UUID player) {
        return new Led(caster, damage, Optional.of(player), armor, leadOrder);
    }

    /**
     * This lead with no attacker recorded, once the attacker died or left reach.
     * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
     *
     * @return the lead recording no attacker
     */
    public Led withoutAttacker() {
        return new Led(caster, damage, Optional.empty(), armor, leadOrder);
    }
}
