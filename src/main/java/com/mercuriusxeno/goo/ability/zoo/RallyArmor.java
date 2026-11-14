package com.mercuriusxeno.goo.ability.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The armor a rallied mob wears from its rallier: a share of the rallier's
 * armor and armor toughness, the armor never under a floor of its own.
 * rally-minions-get-half-your-armor-and-a-floor
 *
 * @param share the share of the rallier's armor and toughness the mob wears
 * @param floor the armor the mob wears however bare its rallier stands
 */
public record RallyArmor(float share, float floor) {

    /** No armor lent: a rally saved before the rally step named any. */
    public static final RallyArmor NONE = new RallyArmor(0f, 0f);

    private static final String FIELD_SHARE = "share";
    private static final String FIELD_FLOOR = "floor";

    /** Saves the rally's armor with the mob. */
    public static final Codec<RallyArmor> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_SHARE).forGetter(RallyArmor::share),
            Codec.FLOAT.fieldOf(FIELD_FLOOR).forGetter(RallyArmor::floor)
    ).apply(inst, RallyArmor::new));

    /** Syncs the rally's armor beside the rest of the rally. */
    public static final StreamCodec<ByteBuf, RallyArmor> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, RallyArmor::share,
            ByteBufCodecs.FLOAT, RallyArmor::floor,
            RallyArmor::new);

    /**
     * The armor the mob wears: the larger of the floor and the share of its rallier's armor.
     *
     * @param rallierArmor the rallier's armor
     * @return the armor the mob gains
     */
    public double armorFrom(double rallierArmor) {
        return Math.max(floor, share * rallierArmor);
    }

    /**
     * The armor toughness the mob wears: the share of its rallier's toughness.
     *
     * @param rallierToughness the rallier's armor toughness
     * @return the toughness the mob gains
     */
    public double toughnessFrom(double rallierToughness) {
        return share * rallierToughness;
    }
}
