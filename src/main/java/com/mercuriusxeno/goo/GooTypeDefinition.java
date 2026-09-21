package com.mercuriusxeno.goo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One goo type as its datapack JSON describes it. Every entry of the
 * {@code goo:goo_type} registry decodes through {@link #CODEC}; the type's
 * id is the registry key the file name gives it, so the body carries only
 * what the id does not derive. Later tasks on the data-driven-types thread
 * move color onto this record.
 *
 * @param peakLight      block light the type emits at and past saturation,
 *                       0 for a type that never glows, at most the vanilla ceiling
 * @param saturationFill fill fraction in (0, 1] at which the type's light
 *                       reaches {@code peakLight}
 */
public record GooTypeDefinition(int peakLight, float saturationFill) {

    /**
     * JSON key of {@link #peakLight}.
     */
    public static final String LIGHT_LEVEL = "light_level";
    /**
     * JSON key of {@link #saturationFill}.
     */
    public static final String SATURATION_FILL = "saturation_fill";
    /**
     * Vanilla block light ceiling, the highest {@link #peakLight} the codec accepts.
     */
    public static final int MAX_LIGHT = 15;

    /**
     * Codec for a goo type JSON body. Decision type-json-light-fields: the
     * light fields come first.
     */
    public static final Codec<GooTypeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, MAX_LIGHT).fieldOf(LIGHT_LEVEL).forGetter(GooTypeDefinition::peakLight),
            Codec.floatRange(Float.MIN_VALUE, 1f).fieldOf(SATURATION_FILL).forGetter(GooTypeDefinition::saturationFill)
    ).apply(instance, GooTypeDefinition::new));
}
