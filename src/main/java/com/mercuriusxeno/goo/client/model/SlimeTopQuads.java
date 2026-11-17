package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mojang.math.Quadrant;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Builds the quads a goo block's sloped slabs draw as, from the heights
 * {@link SlimeTop} reads: each slab, the floor's top or the goo on a wall or
 * under a ceiling, in patches whose corners stand out from its surface at
 * the slab's heights, and down each edge no slab of its goo continues past a
 * rim from the surface out to the slab's edge. The patches split at the
 * slope's bends, 4 and 12 texels in, and at the core's margin, 2 and 14, so
 * each piece of the core's top stands under a piece of the shell of its own
 * extent and the translucent sort draws the core first from any view above.
 * Each face is textured where it stands in its block, as a block model's are.
 * goo-block-top-slopes-to-the-average-with-the-adjoining-block
 * attached-goo-draws-per-attachment-and-divides-its-volume
 * slime-core-draws-translucent-at-the-shells-opacity
 */
public final class SlimeTopQuads {

    /** Where a slab splits into patches along each axis, in model pixels. */
    static final float[] CUTS = {0f, 2f, 4f, 12f, 14f, 16f};
    /** Model pixels across a whole block. */
    private static final float WHOLE = 16f;
    /** Model pixels in one layer. */
    private static final float PIXELS_PER_LAYER = WHOLE / VolumeCellBlock.MAX_LAYERS;
    /** How near a coordinate must stand to a plane to lie on it, in model pixels. */
    private static final float ON_THE_PLANE = 1e-4f;

    private SlimeTopQuads() {
    }

    /**
     * The slab's patches, none for a floor slab goo of its type covers.
     *
     * @param top      the slab's heights
     * @param material the shell's material: the goo type's fluid sprite, under the shell tint, translucent
     * @return one quad for each patch
     */
    public static List<BakedQuad> topOf(SlimeTop top, BakedQuad.MaterialInfo material) {
        List<BakedQuad> quads = new ArrayList<>();
        if (top.covered()) {
            return quads;
        }
        SlimeTop.Plane plane = top.plane();
        for (int a = 0; a + 1 < CUTS.length; a++) {
            for (int b = 0; b + 1 < CUTS.length; b++) {
                Vector3f from = pointOn(plane, CUTS[a], CUTS[b], 0);
                Vector3f to = pointOn(plane, CUTS[a + 1], CUTS[b + 1], 0);
                BakedQuad flat = faceOf(lowCorner(from, to), highCorner(from, to), plane.surface().getOpposite(),
                        material);
                quads.add(reshaped(flat, at -> standOut(at, top)));
            }
        }
        return quads;
    }

    /**
     * The rims down each edge no slab of the block's goo continues past,
     * keyed by the side each faces, so each culls against a neighbour hiding
     * it as a block model's side face does.
     *
     * @param top      the slab's heights and rims
     * @param material the shell's material
     * @return each rim side's quads, one for each patch along its edge
     */
    public static Map<Direction, List<BakedQuad>> rimsOf(SlimeTop top, BakedQuad.MaterialInfo material) {
        Map<Direction, List<BakedQuad>> rims = new EnumMap<>(Direction.class);
        SlimeTop.Plane plane = top.plane();
        for (Direction edge : top.rims()) {
            List<BakedQuad> quads = new ArrayList<>();
            for (int along = 0; along + 1 < CUTS.length; along++) {
                BakedQuad rim = rimPatchOf(top, edge, CUTS[along], CUTS[along + 1], material);
                if (outmostOf(rim, plane) > ON_THE_PLANE) {
                    quads.add(rim);
                }
            }
            rims.put(edge, quads);
        }
        return rims;
    }

    /**
     * One patch of a rim along an edge, from the slab's surface out to the slab's edge.
     *
     * @param top      the slab's heights
     * @param edge     the edge, in the slab's plane
     * @param from     where the patch starts along the edge, in model pixels
     * @param to       where it ends
     * @param material the shell's material
     * @return the rim's patch
     */
    private static BakedQuad rimPatchOf(SlimeTop top, Direction edge, float from, float to,
                                        BakedQuad.MaterialInfo material) {
        SlimeTop.Plane plane = top.plane();
        boolean acrossFirst = edge.getAxis() == plane.first().getAxis();
        float at = edge.getAxisDirection() == Direction.AxisDirection.POSITIVE ? WHOLE : 0;
        Vector3f low = acrossFirst ? pointOn(plane, at, from, 0) : pointOn(plane, from, at, 0);
        Vector3f high = acrossFirst ? pointOn(plane, at, to, WHOLE) : pointOn(plane, to, at, WHOLE);
        BakedQuad flat = faceOf(lowCorner(low, high), highCorner(low, high), edge, material);
        return reshaped(flat, corner -> onTheSurface(corner, plane) ? corner : standOut(corner, top));
    }

    /**
     * A flat quad with its vertices raised to a height field: every vertex
     * of a horizontal patch, or a side's upper vertices, baked at the
     * block's top, its lower ones kept where they were baked.
     *
     * @param flat    the quad baked flat
     * @param heights the height over each point, in blocks
     * @param side    true for a side baked up to the block's top, false for a horizontal patch
     * @return the raised quad
     */
    static BakedQuad raised(BakedQuad flat, HeightField heights, boolean side) {
        return reshaped(flat, at -> !side || at.y() >= WHOLE - ON_THE_PLANE
                ? new Vector3f(at.x(), heights.heightAt(at.x(), at.z()) * WHOLE, at.z()) : at);
    }

    /**
     * @param quad a quad
     * @return the height of its highest vertex, in blocks
     */
    static float tallestOf(BakedQuad quad) {
        float tallest = 0;
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            tallest = Math.max(tallest, quad.position(vertex).y());
        }
        return tallest;
    }

    /**
     * A point in the block given where it stands on a slab's plane.
     *
     * @param plane  the slab's surface and axes
     * @param first  model pixels along the first axis
     * @param second model pixels along the second axis
     * @param out    model pixels out from the surface into the block
     * @return the point, in model pixels
     */
    private static Vector3f pointOn(SlimeTop.Plane plane, float first, float second, float out) {
        float[] coordinates = new float[Direction.Axis.values().length];
        coordinates[plane.first().getAxis().ordinal()] = first;
        coordinates[plane.second().getAxis().ordinal()] = second;
        coordinates[plane.surface().getAxis().ordinal()] = surfaceOf(plane) + outward(plane) * out;
        return new Vector3f(coordinates[Direction.Axis.X.ordinal()], coordinates[Direction.Axis.Y.ordinal()],
                coordinates[Direction.Axis.Z.ordinal()]);
    }

    /**
     * A vertex moved out from the surface to the slab's height at its place.
     *
     * @param at  the vertex, in model pixels
     * @param top the slab's heights
     * @return the vertex standing out at the slab's height
     */
    private static Vector3f standOut(Vector3fc at, SlimeTop top) {
        SlimeTop.Plane plane = top.plane();
        float first = plane.firstOf(at);
        float second = plane.secondOf(at);
        return pointOn(plane, first, second, (float) top.heightAtPixel(first, second) * PIXELS_PER_LAYER);
    }

    private static boolean onTheSurface(Vector3fc at, SlimeTop.Plane plane) {
        return Math.abs(alongTheSurfaceAxis(at, plane) - surfaceOf(plane)) < ON_THE_PLANE;
    }

    /**
     * @param quad  a quad, in block units
     * @param plane the slab's surface and axes
     * @return how far its farthest vertex stands out from the surface, in model pixels
     */
    private static float outmostOf(BakedQuad quad, SlimeTop.Plane plane) {
        float outmost = 0;
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            Vector3fc at = new Vector3f(quad.position(vertex)).mul(WHOLE);
            outmost = Math.max(outmost, Math.abs(alongTheSurfaceAxis(at, plane) - surfaceOf(plane)));
        }
        return outmost;
    }

    private static float alongTheSurfaceAxis(Vector3fc at, SlimeTop.Plane plane) {
        return switch (plane.surface().getAxis()) {
            case X -> at.x();
            case Y -> at.y();
            case Z -> at.z();
        };
    }

    /**
     * @param plane a slab's surface and axes
     * @return where the surface stands along its axis, in model pixels
     */
    private static float surfaceOf(SlimeTop.Plane plane) {
        return plane.surface().getAxisDirection() == Direction.AxisDirection.POSITIVE ? WHOLE : 0;
    }

    /**
     * @param plane a slab's surface and axes
     * @return the sign out from the surface into its block
     */
    private static float outward(SlimeTop.Plane plane) {
        return -plane.surface().getAxisDirection().getStep();
    }

    private static Vector3f lowCorner(Vector3fc one, Vector3fc other) {
        return new Vector3f(one).min(other);
    }

    private static Vector3f highCorner(Vector3fc one, Vector3fc other) {
        return new Vector3f(one).max(other);
    }

    private static BakedQuad faceOf(Vector3fc from, Vector3fc to, Direction face, BakedQuad.MaterialInfo material) {
        return FaceBakery.bakeQuad(SlimeCoreQuads.UNSHARED, from, to, FaceBakery.defaultFaceUV(from, to, face),
                Quadrant.R0, material, face, BlockModelRotation.IDENTITY, null, ExtraFaceData.DEFAULT);
    }

    /**
     * A flat quad with each vertex moved, its texture following each
     * vertex to where it stands in its block, as a block model's face is
     * textured.
     *
     * @param flat the quad baked flat
     * @param move where each vertex moves, in model pixels
     * @return the reshaped quad
     */
    private static BakedQuad reshaped(BakedQuad flat, UnaryOperator<Vector3fc> move) {
        List<Vertex> moved = new ArrayList<>();
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            Vector3fc at = move.apply(new Vector3f(flat.position(vertex)).mul(WHOLE));
            moved.add(new Vertex(new Vector3f(at).div(WHOLE), uvOf(flat.direction(), at,
                    flat.materialInfo().sprite())));
        }
        Iterator<Vertex> inOrder = moved.iterator();
        Vertex first = inOrder.next();
        Vertex second = inOrder.next();
        Vertex third = inOrder.next();
        Vertex fourth = inOrder.next();
        return new BakedQuad(first.position(), second.position(), third.position(), fourth.position(), first.uv(),
                second.uv(), third.uv(), fourth.uv(), flat.direction(), flat.materialInfo());
    }

    /**
     * Where a vertex samples its sprite, by the face it draws and where it
     * stands in its block, as a block model's default face texture runs.
     *
     * @param face   the face the vertex draws
     * @param at     the vertex, in model pixels
     * @param sprite the sprite sampled
     * @return the packed texture coordinate
     */
    private static long uvOf(Direction face, Vector3fc at, TextureAtlasSprite sprite) {
        return UVPair.pack(sprite.getU(Math.clamp(uOf(face, at) / WHOLE, 0f, 1f)),
                sprite.getV(Math.clamp(vOf(face, at) / WHOLE, 0f, 1f)));
    }

    /**
     * @param face the face a vertex draws
     * @param at   the vertex, in model pixels
     * @return its texture's u, in model pixels
     */
    private static float uOf(Direction face, Vector3fc at) {
        return switch (face) {
            case UP, DOWN, SOUTH -> at.x();
            case NORTH -> WHOLE - at.x();
            case WEST -> at.z();
            case EAST -> WHOLE - at.z();
        };
    }

    /**
     * @param face the face a vertex draws
     * @param at   the vertex, in model pixels
     * @return its texture's v, in model pixels
     */
    private static float vOf(Direction face, Vector3fc at) {
        return switch (face) {
            case UP -> at.z();
            case DOWN -> WHOLE - at.z();
            default -> WHOLE - at.y();
        };
    }

    /**
     * One vertex of a reshaped quad.
     *
     * @param position where it stands, in block units
     * @param uv       its packed texture coordinate
     */
    private record Vertex(Vector3fc position, long uv) {
    }

    /**
     * A height over each point of a block's plan.
     */
    @FunctionalInterface
    interface HeightField {

        /**
         * @param x model pixels east of the block's west side
         * @param z model pixels south of its north side
         * @return the height there, in blocks above the block's floor
         */
        float heightAt(float x, float z);
    }
}
