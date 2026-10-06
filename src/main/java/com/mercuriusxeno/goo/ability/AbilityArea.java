package com.mercuriusxeno.goo.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * The area an ability affects, which the glove draws at the aim point while
 * right click is held, in place of chain marker overlays: a sphere around the
 * point, a line from the hand to it, or a cone from the hand toward it.
 * right-click-held-previews-release-throws
 *
 * @param shape the area's shape
 * @param size  the sphere's radius or the cone's length, in blocks; a line runs from the hand to the point
 * @param angle the cone's half-angle in degrees, 0 for every other shape
 */
public record AbilityArea(Shape shape, double size, double angle) {

    /** An ability with no area, which shows the reticule alone. */
    public static final AbilityArea NONE = new AbilityArea(Shape.NONE, 0, 0);

    private static final String FIELD_SHAPE = "shape";
    private static final String FIELD_SIZE = "size";
    private static final String FIELD_ANGLE = "angle";

    /** Datapack codec for an ability JSON's area block. */
    public static final Codec<AbilityArea> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Shape.CODEC.fieldOf(FIELD_SHAPE).forGetter(AbilityArea::shape),
            Codec.DOUBLE.optionalFieldOf(FIELD_SIZE, 0.0).forGetter(AbilityArea::size),
            Codec.DOUBLE.optionalFieldOf(FIELD_ANGLE, 0.0).forGetter(AbilityArea::angle)
    ).apply(inst, AbilityArea::new));

    /** Network codec, so the client draws the area from the synced ability. */
    public static final StreamCodec<ByteBuf, AbilityArea> STREAM_CODEC = StreamCodec.composite(
            Shape.STREAM_CODEC, AbilityArea::shape,
            ByteBufCodecs.DOUBLE, AbilityArea::size,
            ByteBufCodecs.DOUBLE, AbilityArea::angle,
            AbilityArea::new);

    /** The shapes an area takes. */
    public enum Shape implements StringRepresentable {
        /** No area. */
        NONE("none"),
        /** A sphere around the aim point. */
        SPHERE("sphere"),
        /** A line from the hand to the aim point. */
        LINE("line"),
        /** A cone from the hand toward the aim point. */
        CONE("cone");

        /** Datapack codec, reading the shape by its word. */
        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);

        /** Network codec, writing the shape as a varint ordinal. */
        public static final StreamCodec<ByteBuf, Shape> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(ordinal -> values()[ordinal], Shape::ordinal);

        private final String serializedName;

        Shape(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public @NonNull String getSerializedName() {
            return serializedName;
        }
    }
}
