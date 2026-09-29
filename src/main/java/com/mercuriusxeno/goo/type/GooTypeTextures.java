package com.mercuriusxeno.goo.type;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/**
 * The textures a goo type JSON names for its goo and its fluid (decision
 * type-named-textures). Every field is optional: a texture the JSON leaves
 * unnamed renders as the grey base tinted by the type's highlight color.
 *
 * @param gooTiny     sprite of the tiny goo model, on the item atlas
 * @param gooSmall    sprite of the small goo model, on the item atlas
 * @param gooBase     sprite of the base goo model, on the item atlas
 * @param gooLarge    sprite of the large goo model, on the item atlas
 * @param fluidStill   still fluid sprite, on the block atlas
 * @param fluidFlowing flowing fluid sprite, on the block atlas
 */
public record GooTypeTextures(Optional<Identifier> gooTiny, Optional<Identifier> gooSmall,
                              Optional<Identifier> gooBase, Optional<Identifier> gooLarge,
                              Optional<Identifier> fluidStill, Optional<Identifier> fluidFlowing) {

    /**
     * JSON key of {@link #gooTiny}.
     */
    public static final String GOO_TINY = "goo_tiny";
    /**
     * JSON key of {@link #gooSmall}.
     */
    public static final String GOO_SMALL = "goo_small";
    /**
     * JSON key of {@link #gooBase}.
     */
    public static final String GOO_BASE = "goo_base";
    /**
     * JSON key of {@link #gooLarge}.
     */
    public static final String GOO_LARGE = "goo_large";
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
            Identifier.CODEC.optionalFieldOf(GOO_TINY).forGetter(GooTypeTextures::gooTiny),
            Identifier.CODEC.optionalFieldOf(GOO_SMALL).forGetter(GooTypeTextures::gooSmall),
            Identifier.CODEC.optionalFieldOf(GOO_BASE).forGetter(GooTypeTextures::gooBase),
            Identifier.CODEC.optionalFieldOf(GOO_LARGE).forGetter(GooTypeTextures::gooLarge),
            Identifier.CODEC.optionalFieldOf(FLUID_STILL).forGetter(GooTypeTextures::fluidStill),
            Identifier.CODEC.optionalFieldOf(FLUID_FLOWING).forGetter(GooTypeTextures::fluidFlowing)
    ).apply(instance, GooTypeTextures::new));

    /**
     * The goo sprite the JSON names for a model size.
     *
     * @param size the goo model size
     * @return the named sprite, or empty when the JSON names none for that size
     */
    public Optional<Identifier> goo(GooModelSize size) {
        return switch (size) {
            case TINY -> gooTiny;
            case SMALL -> gooSmall;
            case BASE -> gooBase;
            case LARGE -> gooLarge;
        };
    }
}
