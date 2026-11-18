package com.mercuriusxeno.goo.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The name and the icon an ability shows a player standing in one dimension, in place of its own: one ability, one
 * key and one cost, showing one face or the other by where the player stands.
 * decision lunar-reads-as-terran-while-you-are-on-the-moon
 *
 * @param dimension   the dimension the face shows in
 * @param displayName the translation key for the name it shows there
 * @param icon        the texture path for the icon it shows there, empty for the ability's convention path
 */
public record DimensionFace(ResourceKey<Level> dimension, String displayName, String icon) {

    private static final String NO_ICON = "";

    /** Reads a face from an ability's JSON. */
    public static final Codec<DimensionFace> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(DimensionFace::dimension),
            Codec.STRING.fieldOf("displayName").forGetter(DimensionFace::displayName),
            Codec.STRING.optionalFieldOf("icon", NO_ICON).forGetter(DimensionFace::icon)
    ).apply(inst, DimensionFace::new));

    /** Sends a face beside its ability to the client. */
    public static final StreamCodec<ByteBuf, DimensionFace> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), DimensionFace::dimension,
            ByteBufCodecs.STRING_UTF8, DimensionFace::displayName,
            ByteBufCodecs.STRING_UTF8, DimensionFace::icon,
            DimensionFace::new);
}
