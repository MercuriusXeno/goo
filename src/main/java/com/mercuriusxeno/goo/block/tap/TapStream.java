package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;

/**
 * The stream a tap pours at 1:1, synced for its renderer to draw from the
 * spigot down to the landing (decision one-to-one-draws-a-stream).
 *
 * @param type     the goo type pouring
 * @param surfaceY the world Y of the surface the stream lands on
 */
public record TapStream(ResourceKey<GooTypeDefinition> type, double surfaceY) {

    /**
     * Save and sync codec.
     */
    public static final Codec<TapStream> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GooTypes.KEY_CODEC.fieldOf("type").forGetter(TapStream::type),
            Codec.DOUBLE.fieldOf("surface_y").forGetter(TapStream::surfaceY)
    ).apply(instance, TapStream::new));

    /**
     * The block-local Y of the spigot's underside, where the stream leaves the tap.
     */
    public static final double SPIGOT_UNDERSIDE_LOCAL_Y = TapSpigot.underside(BlockPos.ZERO).y;
}
