package com.mercuriusxeno.goo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * One goo type as its datapack JSON describes it. Every entry of the
 * {@code goo:goo_type} registry decodes through {@link #CODEC}; the type's
 * id is the registry key the file name gives it, so the body carries only
 * what the id does not derive. Later tasks on the data-driven-types thread
 * move light and color onto this record one property group at a time.
 */
public record GooTypeDefinition() {

    /**
     * Codec for a goo type JSON body. Decision datapack-goo-registry: the
     * body holds no id-derived field, so it decodes from an empty object.
     */
    public static final Codec<GooTypeDefinition> CODEC =
            MapCodec.unit(GooTypeDefinition::new).codec();
}
