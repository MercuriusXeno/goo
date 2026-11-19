package com.mercuriusxeno.goo.throwing;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The cross-section of a spray's cone: how wide it opens across the caster's left and right and how wide up and
 * down, apex to rim, in degrees. A round cone opens alike both ways; a sprayer's nozzle narrows, widens or flattens
 * it.
 * decision spray-item-takes-different-nozzles-doing-different-things
 * decision fan-nozzle-flattens-and-widens-the-cone
 *
 * @param sideDegrees how wide the cone opens across, apex to rim
 * @param liftDegrees how wide the cone opens up and down, apex to rim
 * @param band        whether the spray is a flat band, its drips spread evenly from end to end across and evenly
 *                    through its thin height, in place of a cone's drips spread evenly by area
 */
public record ConeShape(double sideDegrees, double liftDegrees, boolean band) {

    /** Network codec. */
    public static final StreamCodec<ByteBuf, ConeShape> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, ConeShape::sideDegrees,
            ByteBufCodecs.DOUBLE, ConeShape::liftDegrees,
            ByteBufCodecs.BOOL, ConeShape::band,
            ConeShape::new);

    /**
     * A round cone, as every glove spray throws.
     *
     * @param coneDegrees the cone, apex to rim, in degrees
     * @return the round cone
     */
    public static ConeShape round(double coneDegrees) {
        return new ConeShape(coneDegrees, coneDegrees, false);
    }

    /**
     * How wide the cone opens at a turn about its axis: the radius of the ellipse its two widths make there, the
     * one width wherever the cone is round.
     *
     * @param turn the turn about the axis from across, radians
     * @return the cone's width there, apex to rim, in degrees
     */
    double degreesAt(double turn) {
        if (sideDegrees == liftDegrees) {
            return sideDegrees;
        }
        double across = liftDegrees * Math.cos(turn);
        double upDown = sideDegrees * Math.sin(turn);
        return sideDegrees * liftDegrees / Math.sqrt(across * across + upDown * upDown);
    }
}
