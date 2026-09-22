package com.mercuriusxeno.goo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.material.MapColor;
import java.util.Locale;

/**
 * One goo type as its datapack JSON describes it. Every entry of the
 * {@code goo:goo_type} registry decodes through {@link #CODEC}; the type's
 * id is the registry key the file name gives it, so the body carries only
 * what the id does not derive.
 *
 * @param peakLight      block light the type emits at and past saturation,
 *                       0 for a type that never glows, at most the vanilla ceiling
 * @param saturationFill fill fraction in (0, 1] at which the type's light
 *                       reaches {@code peakLight}
 * @param wheel          RGB of the type's radial menu segment
 * @param bright         RGB of the segment while hovered
 * @param highlight      RGB of the aim arc, ghost fill and fade walls
 * @param edge           RGB of wireframe contours and outlines
 * @param density        fluid density on the water = 1000 scale
 * @param viscosity      fluid viscosity on the water = 1000 scale, higher is thicker
 * @param temperature    fluid temperature in kelvin, room temperature 300
 * @param extinguishes   whether the fluid puts out a burning entity
 * @param mapColor       the color the fluid block paints on a map
 * @param textures       the blob and fluid sprites the JSON names, each optional
 */
public record GooTypeDefinition(int peakLight, float saturationFill, int wheel, int bright, int highlight, int edge,
                                int density, int viscosity, int temperature, boolean extinguishes, MapColor mapColor,
                                GooTypeTextures textures) {

    /**
     * JSON key of {@link #peakLight}.
     */
    public static final String LIGHT_LEVEL = "light_level";
    /**
     * JSON key of {@link #saturationFill}.
     */
    public static final String SATURATION_FILL = "saturation_fill";
    /**
     * JSON key of {@link #wheel}.
     */
    public static final String WHEEL = "wheel";
    /**
     * JSON key of {@link #bright}.
     */
    public static final String BRIGHT = "bright";
    /**
     * JSON key of {@link #highlight}.
     */
    public static final String HIGHLIGHT = "highlight";
    /**
     * JSON key of {@link #edge}.
     */
    public static final String EDGE = "edge";
    /**
     * JSON key of {@link #density}.
     */
    public static final String DENSITY = "density";
    /**
     * JSON key of {@link #viscosity}.
     */
    public static final String VISCOSITY = "viscosity";
    /**
     * JSON key of {@link #temperature}.
     */
    public static final String TEMPERATURE = "temperature";
    /**
     * JSON key of {@link #extinguishes}.
     */
    public static final String EXTINGUISHES = "extinguishes";
    /**
     * JSON key of {@link #mapColor}.
     */
    public static final String MAP_COLOR = "map_color";
    /**
     * JSON key of {@link #textures}.
     */
    public static final String TEXTURES = "textures";
    /**
     * Vanilla block light ceiling, the highest {@link #peakLight} the codec accepts.
     */
    public static final int MAX_LIGHT = 15;

    private static final int HEX_RADIX = 16;
    private static final int HEX_DIGITS = 6;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final String HEX_FORMAT = "%06X";
    private static final String NOT_HEX = "Not a six-digit hex RGB color: ";

    /**
     * An RGB color written as six hex digits, {@code "FF6600"}, the spelling
     * the color config used before decision type-json-colors moved the
     * channels into the type JSON.
     */
    public static final Codec<Integer> HEX_COLOR = Codec.STRING.comapFlatMap(
            GooTypeDefinition::parseHexColor, GooTypeDefinition::formatHexColor);

    /**
     * Codec for a goo type JSON body. Decision type-json-light-fields: the
     * light fields come first; decision generic-goo-fluids: the fluid fields
     * follow the colors; decision type-named-textures: the textures object
     * closes the body and may be left out.
     */
    public static final Codec<GooTypeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, MAX_LIGHT).fieldOf(LIGHT_LEVEL).forGetter(GooTypeDefinition::peakLight),
            Codec.floatRange(Float.MIN_VALUE, 1f).fieldOf(SATURATION_FILL).forGetter(GooTypeDefinition::saturationFill),
            HEX_COLOR.fieldOf(WHEEL).forGetter(GooTypeDefinition::wheel),
            HEX_COLOR.fieldOf(BRIGHT).forGetter(GooTypeDefinition::bright),
            HEX_COLOR.fieldOf(HIGHLIGHT).forGetter(GooTypeDefinition::highlight),
            HEX_COLOR.fieldOf(EDGE).forGetter(GooTypeDefinition::edge),
            Codec.INT.fieldOf(DENSITY).forGetter(GooTypeDefinition::density),
            Codec.INT.fieldOf(VISCOSITY).forGetter(GooTypeDefinition::viscosity),
            Codec.INT.fieldOf(TEMPERATURE).forGetter(GooTypeDefinition::temperature),
            Codec.BOOL.fieldOf(EXTINGUISHES).forGetter(GooTypeDefinition::extinguishes),
            MapColors.CODEC.fieldOf(MAP_COLOR).forGetter(GooTypeDefinition::mapColor),
            GooTypeTextures.CODEC.optionalFieldOf(TEXTURES, GooTypeTextures.NONE).forGetter(GooTypeDefinition::textures)
    ).apply(instance, GooTypeDefinition::new));

    /**
     * Parses six hex digits into an RGB int.
     *
     * @param hex the digits, case-insensitive
     * @return the color, or an error naming the text that failed
     */
    static DataResult<Integer> parseHexColor(String hex) {
        if (hex.length() != HEX_DIGITS || !hex.chars().allMatch(GooTypeDefinition::isHexDigit)) {
            return DataResult.error(() -> NOT_HEX + hex);
        }
        return DataResult.success(Integer.parseInt(hex, HEX_RADIX));
    }

    /**
     * Writes an RGB int as six upper-case hex digits.
     *
     * @param rgb the color; bits above the low 24 are dropped
     * @return the digits
     */
    static String formatHexColor(int rgb) {
        return String.format(Locale.ROOT, HEX_FORMAT, rgb & RGB_MASK);
    }

    private static boolean isHexDigit(int c) {
        return Character.digit(c, HEX_RADIX) >= 0;
    }
}
