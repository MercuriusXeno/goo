package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * One vine strand drawn as a ribbon of the vine texture along a spine: a
 * quadratic curve from where the strand leaves to where it latches, grown
 * part of the way along while it reaches out. The vine sprite repeats every
 * half block along the strand, so its leaves keep their size however long
 * the strand runs, a strand reaching back to a root blocks away included,
 * and every quad is emitted both ways round so the ribbon shows from either
 * side.
 * vines-unpack-root-and-thorn
 */
public final class VineRibbon {

    /** The vine sprite on the block atlas, a grey texture vanilla tints by foliage. */
    public static final Identifier VINE_SPRITE = Identifier.withDefaultNamespace("block/vine");

    /** Segments one strand is cut into. */
    static final int SEGMENTS = 8;

    /** How far along a strand one vine sprite runs before it repeats, in blocks. */
    static final double SPRITE_BLOCKS = 0.5;

    /** A length short enough to read as none, so a piece ending on a sprite's edge starts the next sprite. */
    private static final double NONE = 1e-9;

    /** The weight a quadratic curve's middle term gives its control point. */
    private static final double CONTROL_WEIGHT = 2;

    /** A knot stands this many times as tall as it is half wide. */
    private static final double KNOT_RISE = 1.4;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 ACROSS = new Vec3(1, 0, 0);

    private VineRibbon() {
    }

    /**
     * The vine sprite's UV rect on the block atlas.
     *
     * @return the UV rect
     */
    public static GooRenderUtil.UvRect spriteUv() {
        TextureAtlasSprite sprite = GooSubmitter.blockSprite(VINE_SPRITE);
        return GooSubmitter.spriteUv(sprite);
    }

    /**
     * The points along a strand: a quadratic curve from its start to its end
     * bent toward the control point, cut from the start to as far as the
     * strand has grown.
     *
     * @param from    where the strand leaves
     * @param control the point the curve bends toward
     * @param to      where the strand latches
     * @param grown   how far along the strand has reached, 0 to 1
     * @return SEGMENTS + 1 points, the first at the start
     */
    static List<Vec3> curve(Vec3 from, Vec3 control, Vec3 to, float grown) {
        List<Vec3> points = new ArrayList<>(SEGMENTS + 1);
        for (int k = 0; k <= SEGMENTS; k++) {
            double t = grown * k / (double) SEGMENTS;
            double u = 1 - t;
            points.add(from.scale(u * u).add(control.scale(CONTROL_WEIGHT * u * t)).add(to.scale(t * t)));
        }
        return points;
    }

    /**
     * One stretch of a strand drawn as one quad: part of one spine segment,
     * showing part of one vine sprite.
     *
     * @param segment the spine segment it lies on
     * @param from    where along the segment it starts, 0 to 1
     * @param to      where along the segment it ends, 0 to 1
     * @param vFrom   where down the sprite it starts, 0 to 1
     * @param vTo     where down the sprite it ends, 0 to 1
     */
    record Piece(int segment, double from, double to, float vFrom, float vTo) {
    }

    /**
     * Cuts a strand's segments into pieces by length, so the sprite runs
     * down a set distance and starts again: no piece crosses a sprite's edge,
     * and a segment crossing one splits there.
     *
     * @param lengths      each spine segment's length, in blocks
     * @param spriteBlocks how far one sprite runs along the strand
     * @return the pieces, in order along the strand
     */
    static List<Piece> tilePieces(List<Double> lengths, double spriteBlocks) {
        List<Piece> pieces = new ArrayList<>();
        double intoSprite = 0;
        for (int k = 0; k < lengths.size(); k++) {
            double length = lengths.get(k);
            double at = 0;
            while (length - at > NONE) {
                double step = Math.min(spriteBlocks - intoSprite, length - at);
                pieces.add(new Piece(k, at / length, (at + step) / length, (float) (intoSprite / spriteBlocks),
                        (float) ((intoSprite + step) / spriteBlocks)));
                at += step;
                intoSprite += step;
                if (spriteBlocks - intoSprite <= NONE) {
                    intoSprite = 0;
                }
            }
        }
        return pieces;
    }

    /**
     * Emits a ribbon along a spine, each point widened either side by its own
     * side vector, the sprite repeating along its length.
     *
     * @param ctx    the render context, carrying the tint and the light
     * @param spine  the points along the strand
     * @param sides  the half-width offset at each point
     * @param normal the normal the ribbon is lit by
     * @param uv     the vine sprite's UV rect
     */
    static void emit(RenderContext ctx, List<Vec3> spine, List<Vec3> sides, Vec3 normal, GooRenderUtil.UvRect uv) {
        List<Double> lengths = new ArrayList<>(spine.size());
        for (int k = 0; k + 1 < spine.size(); k++) {
            lengths.add(spine.get(k).distanceTo(spine.get(k + 1)));
        }
        float height = uv.v1() - uv.v0();
        for (Piece piece : tilePieces(lengths, SPRITE_BLOCKS)) {
            int k = piece.segment();
            Vec3 a = spine.get(k).lerp(spine.get(k + 1), piece.from());
            Vec3 b = spine.get(k).lerp(spine.get(k + 1), piece.to());
            Vec3 sa = sides.get(k).lerp(sides.get(k + 1), piece.from());
            Vec3 sb = sides.get(k).lerp(sides.get(k + 1), piece.to());
            float v0 = uv.v0() + height * piece.vFrom();
            float v1 = uv.v0() + height * piece.vTo();
            Vec3[] corners = {a.subtract(sa), a.add(sa), b.add(sb), b.subtract(sb)};
            float[] us = {uv.u0(), uv.u1(), uv.u1(), uv.u0()};
            float[] vs = {v0, v0, v1, v1};
            for (int i = 0; i < corners.length; i++) {
                vertex(ctx, corners[i], us[i], vs[i], normal);
            }
            for (int i = corners.length - 1; i >= 0; i--) {
                vertex(ctx, corners[i], us[i], vs[i], normal.reverse());
            }
        }
    }

    /**
     * Emits a strand as two ribbons crossed along its spine, as a vanilla
     * plant crosses two quads, so it shows its body from every side.
     *
     * @param ctx       the render context, carrying the tint and the light
     * @param spine     the points along the strand
     * @param halfWidth half the strand's width
     * @param uv        the vine sprite's UV rect
     */
    static void emitCrossed(RenderContext ctx, List<Vec3> spine, double halfWidth, GooRenderUtil.UvRect uv) {
        List<Vec3> flat = new ArrayList<>(spine.size());
        List<Vec3> upright = new ArrayList<>(spine.size());
        for (int k = 0; k < spine.size(); k++) {
            Vec3 along = spine.get(Math.min(k + 1, spine.size() - 1)).subtract(spine.get(Math.max(k - 1, 0)));
            Vec3 side = along.cross(UP);
            side = side.lengthSqr() == 0 ? ACROSS : side.normalize();
            Vec3 other = along.lengthSqr() == 0 ? UP : along.cross(side).normalize();
            flat.add(side.scale(halfWidth));
            upright.add(other.scale(halfWidth));
        }
        emit(ctx, spine, flat, UP, uv);
        emit(ctx, spine, upright, ACROSS, uv);
    }

    /**
     * Emits a knot of vine standing on a face: two crossed upright quads.
     *
     * @param ctx    the render context
     * @param center where the knot stands on the face
     * @param normal the face's unit normal, the way the knot stands up
     * @param across one unit direction across the face
     * @param along  the other unit direction across the face
     * @param size   the knot's half-width, and its height its rise scales by
     * @param uv     the vine sprite's UV rect
     */
    static void emitKnot(RenderContext ctx, Vec3 center, Vec3 normal, Vec3 across, Vec3 along, double size,
                         GooRenderUtil.UvRect uv) {
        Vec3 up = normal.scale(size * KNOT_RISE);
        for (Vec3 side : List.of(across, along)) {
            Vec3 half = side.scale(size);
            emit(ctx, List.of(center, center.add(up)), List.of(half, half), normal, uv);
        }
    }

    private static void vertex(RenderContext ctx, Vec3 at, float u, float v, Vec3 normal) {
        ctx.vertex((float) at.x, (float) at.y, (float) at.z, u, v, (float) normal.x, (float) normal.y,
                (float) normal.z);
    }
}
