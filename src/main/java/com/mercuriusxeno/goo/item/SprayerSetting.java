package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import java.util.Optional;

/**
 * What the sprayer's wheel set and the sprayer keeps: the goo type picked, or none, so the canister's most held
 * sprayable type sprays, the flow and the nozzle.
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 * decision spray-item-sprays-more-or-less-per-tick
 * decision spray-item-takes-different-nozzles-doing-different-things
 *
 * @param pickedType the goo type picked on the wheel, empty before any pick
 * @param flow       the flow picked on the wheel
 * @param nozzle     the nozzle picked on the wheel
 */
public record SprayerSetting(Optional<ResourceKey<GooTypeDefinition>> pickedType, SprayerFlow flow,
                             SprayerNozzle nozzle) {

    /** A sprayer nothing was picked on: the canister's most held sprayable type at the middle flow, no nozzle. */
    public static final SprayerSetting DEFAULT = new SprayerSetting(Optional.empty(), SprayerFlow.MEDIUM,
            SprayerNozzle.OPEN);

    /** Persistent codec. */
    public static final Codec<SprayerSetting> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GooTypes.KEY_CODEC.optionalFieldOf("type").forGetter(SprayerSetting::pickedType),
            SprayerFlow.CODEC.optionalFieldOf("flow", SprayerFlow.MEDIUM).forGetter(SprayerSetting::flow),
            SprayerNozzle.CODEC.optionalFieldOf("nozzle", SprayerNozzle.OPEN).forGetter(SprayerSetting::nozzle)
    ).apply(instance, SprayerSetting::new));

    /** Network codec. */
    public static final StreamCodec<ByteBuf, SprayerSetting> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(GooTypes.KEY_STREAM_CODEC), SprayerSetting::pickedType,
            SprayerFlow.STREAM_CODEC, SprayerSetting::flow,
            SprayerNozzle.STREAM_CODEC, SprayerSetting::nozzle,
            SprayerSetting::new);
}
