package com.mercuriusxeno.goo.ability.yarn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The armor a led mob wears from its leader: a share of the leader's
 * armor and armor toughness, the armor never under a floor of its own.
 * rally-minions-get-half-your-armor-and-a-floor
 *
 * @param share the share of the leader's armor and toughness the mob wears
 * @param floor the armor the mob wears however bare its leader stands
 */
public record LeadArmor(float share, float floor) {

    /** No armor lent: a lead saved before the lead step named any. */
    public static final LeadArmor NONE = new LeadArmor(0f, 0f);

    private static final String FIELD_SHARE = "share";
    private static final String FIELD_FLOOR = "floor";

    /** Saves the lead's armor with the mob. */
    public static final Codec<LeadArmor> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_SHARE).forGetter(LeadArmor::share),
            Codec.FLOAT.fieldOf(FIELD_FLOOR).forGetter(LeadArmor::floor)
    ).apply(inst, LeadArmor::new));

    /** Syncs the lead's armor beside the rest of the lead. */
    public static final StreamCodec<ByteBuf, LeadArmor> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, LeadArmor::share,
            ByteBufCodecs.FLOAT, LeadArmor::floor,
            LeadArmor::new);

    /**
     * The armor the mob wears: the larger of the floor and the share of its leader's armor.
     *
     * @param leaderArmor the leader's armor
     * @return the armor the mob gains
     */
    public double armorFrom(double leaderArmor) {
        return Math.max(floor, share * leaderArmor);
    }

    /**
     * The armor toughness the mob wears: the share of its leader's toughness.
     *
     * @param leaderToughness the leader's armor toughness
     * @return the toughness the mob gains
     */
    public double toughnessFrom(double leaderToughness) {
        return share * leaderToughness;
    }
}
