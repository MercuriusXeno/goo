package com.mercuriusxeno.goo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/**
 * The textures a goo type JSON names for its blob and its fluid (decision
 * type-named-textures). Every field is optional: a texture the JSON leaves
 * unnamed renders as the grey base tinted by the type's highlight color.
 *
 * @param blobTiny     sprite of the tiny blob model, on the item atlas
 * @param blobSmall    sprite of the small blob model, on the item atlas
 * @param blobBase     sprite of the base blob model, on the item atlas
 * @param blobLarge    sprite of the large blob model, on the item atlas
 * @param fluidStill   still fluid sprite, on the block atlas
 * @param fluidFlowing flowing fluid sprite, on the block atlas
 */
public record GooTypeTextures(Optional<Identifier> blobTiny, Optional<Identifier> blobSmall,
                              Optional<Identifier> blobBase, Optional<Identifier> blobLarge,
                              Optional<Identifier> fluidStill, Optional<Identifier> fluidFlowing) {

    /**
     * JSON key of {@link #blobTiny}.
     */
    public static final String BLOB_TINY = "blob_tiny";
    /**
     * JSON key of {@link #blobSmall}.
     */
    public static final String BLOB_SMALL = "blob_small";
    /**
     * JSON key of {@link #blobBase}.
     */
    public static final String BLOB_BASE = "blob_base";
    /**
     * JSON key of {@link #blobLarge}.
     */
    public static final String BLOB_LARGE = "blob_large";
    /**
     * JSON key of {@link #fluidStill}.
     */
    public static final String FLUID_STILL = "fluid_still";
    /**
     * JSON key of {@link #fluidFlowing}.
     */
    public static final String FLUID_FLOWING = "fluid_flowing";

    /**
     * The textures of a type whose JSON names none.
     */
    public static final GooTypeTextures NONE = new GooTypeTextures(Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    /**
     * Codec of the {@code textures} object of a goo type JSON.
     */
    public static final Codec<GooTypeTextures> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf(BLOB_TINY).forGetter(GooTypeTextures::blobTiny),
            Identifier.CODEC.optionalFieldOf(BLOB_SMALL).forGetter(GooTypeTextures::blobSmall),
            Identifier.CODEC.optionalFieldOf(BLOB_BASE).forGetter(GooTypeTextures::blobBase),
            Identifier.CODEC.optionalFieldOf(BLOB_LARGE).forGetter(GooTypeTextures::blobLarge),
            Identifier.CODEC.optionalFieldOf(FLUID_STILL).forGetter(GooTypeTextures::fluidStill),
            Identifier.CODEC.optionalFieldOf(FLUID_FLOWING).forGetter(GooTypeTextures::fluidFlowing)
    ).apply(instance, GooTypeTextures::new));

    /**
     * The blob sprite the JSON names for a model size.
     *
     * @param size the blob model size
     * @return the named sprite, or empty when the JSON names none for that size
     */
    public Optional<Identifier> blob(BlobModelSize size) {
        return switch (size) {
            case TINY -> blobTiny;
            case SMALL -> blobSmall;
            case BASE -> blobBase;
            case LARGE -> blobLarge;
        };
    }
}
