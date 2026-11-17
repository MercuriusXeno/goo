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
import java.util.Collections;
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
     * The faces joining this slab to the slab round each outside edge it
     * joins: along the edge one sloped face from this slab's edge to the
     * other's, outside the block's cube, and a triangle closing each end
     * where the slab draws a rim. Of the two slabs one draws them, the one
     * whose edge's direction comes later, so they draw once.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     *
     * @param top      the slab's heights and joins
     * @param material the shell's material
     * @return the joining faces
     */
    public static List<BakedQuad> joinsOf(SlimeTop top, BakedQuad.MaterialInfo material) {
        List<BakedQuad> quads = new ArrayList<>();
        Direction surface = top.plane().surface();
        for (Direction edge : top.joins()) {
            if (edge.ordinal() > surface.getOpposite().ordinal()) {
                quads.addAll(joinAlong(top, top.across(edge), edge, material));
            }
        }
        return quads;
    }

    /**
     * The faces along one joined edge.
     *
     * @param mine     this slab's heights
     * @param theirs   the slab round the edge, read in its own block
     * @param edge     the edge, in this slab's plane
     * @param material the shell's material
     * @return the sloped faces along the edge and the triangles closing its ends
     */
    private static List<BakedQuad> joinAlong(SlimeTop mine, SlimeTop theirs, Direction edge,
                                             BakedQuad.MaterialInfo material) {
        SlimeTop.Plane plane = mine.plane();
        Direction along = edge.getAxis() == plane.first().getAxis() ? plane.second() : plane.first();
        Vector3f outward = new Vector3f(plane.surface().getOpposite().getUnitVec3f());
        Vector3f turned = new Vector3f(edge.getUnitVec3f());
        Vector3f offset = new Vector3f(edge.getUnitVec3f()).add(plane.surface().getUnitVec3f()).mul(WHOLE);
        List<BakedQuad> quads = new ArrayList<>();
        Vector3f[] lastMine = null;
        for (float cut : CUTS) {
            Vector3f corner = edgePoint(plane, edge, along, cut);
            float myHeight = heightThere(mine, corner) * PIXELS_PER_LAYER;
            float theirHeight = heightThere(theirs, new Vector3f(corner).sub(offset)) * PIXELS_PER_LAYER;
            Vector3f[] here = {new Vector3f(corner).fma(myHeight, outward), new Vector3f(corner).fma(theirHeight,
                    turned), corner};
            if (lastMine != null) {
                quads.add(quadThrough(List.of(lastMine[0], here[0], here[1], lastMine[1]),
                        new Vector3f(turned).add(outward), edge, material));
            }
            lastMine = here;
        }
        quads.addAll(endsOf(mine, edge, along, material, outward, turned, offset, theirs));
        return quads;
    }

    /**
     * The triangles closing a joined edge's ends, each where this slab draws
     * a rim on the edge it meets there.
     *
     * @param mine     this slab's heights
     * @param edge     the joined edge
     * @param along    the axis the edge runs along, by its positive direction
     * @param material the shell's material
     * @param outward  the unit out from this slab's surface
     * @param turned   the unit out from the slab round the edge
     * @param offset   the block round the edge, in model pixels
     * @param theirs   the slab round the edge
     * @return the closing triangles
     */
    private static List<BakedQuad> endsOf(SlimeTop mine, Direction edge, Direction along,
                                          BakedQuad.MaterialInfo material, Vector3fc outward, Vector3fc turned,
                                          Vector3fc offset, SlimeTop theirs) {
        List<BakedQuad> ends = new ArrayList<>();
        for (Direction end : List.of(along.getOpposite(), along)) {
            if (mine.rims().contains(end)) {
                float at = end == along ? WHOLE : 0;
                Vector3f corner = edgePoint(mine.plane(), edge, along, at);
                Vector3f myEdge = new Vector3f(corner).fma(heightThere(mine, corner) * PIXELS_PER_LAYER, outward);
                Vector3f theirEdge = new Vector3f(corner).fma(heightThere(theirs, new Vector3f(corner).sub(offset))
                        * PIXELS_PER_LAYER, turned);
                ends.add(quadThrough(List.of(corner, myEdge, theirEdge, theirEdge), end.getUnitVec3f(), end,
                        material));
            }
        }
        return ends;
    }

    /**
     * A point on a slab's edge, on the slab's surface.
     *
     * @param plane the slab's surface and axes
     * @param edge  the edge
     * @param along the axis the edge runs along, by its positive direction
     * @param at    where along it, in model pixels
     * @return the point, in model pixels
     */
    private static Vector3f edgePoint(SlimeTop.Plane plane, Direction edge, Direction along, float at) {
        float onTheEdge = edge.getAxisDirection() == Direction.AxisDirection.POSITIVE ? WHOLE : 0;
        return along == plane.first() ? pointOn(plane, at, onTheEdge, 0) : pointOn(plane, onTheEdge, at, 0);
    }

    /**
     * @param slab   a slab's heights
     * @param pixels a point on its surface in its own block, in model pixels
     * @return the slab's height there, in layers
     */
    private static float heightThere(SlimeTop slab, Vector3fc pixels) {
        return (float) slab.heightAtPixel(slab.plane().firstOf(pixels), slab.plane().secondOf(pixels));
    }

    /**
     * A quad through four points, wound to face one way.
     *
     * @param corners  the points, in model pixels, in order round the quad
     * @param facing   the way the quad should face
     * @param face     the side it draws as
     * @param material the shell's material
     * @return the quad
     */
    private static BakedQuad quadThrough(List<Vector3f> corners, Vector3fc facing, Direction face,
                                         BakedQuad.MaterialInfo material) {
        Vector3f across = new Vector3f(corners.get(1)).sub(corners.getFirst());
        Vector3f down = new Vector3f(corners.getLast()).sub(corners.getFirst());
        boolean facesAway = new Vector3f(across).cross(down).dot(facing) < 0;
        List<Vector3f> wound = new ArrayList<>(corners);
        if (facesAway) {
            Collections.reverse(wound);
        }
        List<Vertex> vertices = new ArrayList<>();
        for (Vector3f corner : wound) {
            vertices.add(new Vertex(new Vector3f(corner).div(WHOLE), uvOf(face, corner, material.sprite())));
        }
        return quadOf(vertices, face, material);
    }

    /**
     * A flat face baked between the block's floor and top with its vertices
     * moved onto two height fields: those at the floor onto the low one,
     * those at the top onto the high one.
     *
     * @param flat the face baked flat
     * @param low  the height each floor vertex moves to, in blocks
     * @param high the height each top vertex moves to, in blocks
     * @return the moved face
     */
    static BakedQuad spanned(BakedQuad flat, HeightField low, HeightField high) {
        return reshaped(flat, at -> {
            HeightField field = at.y() >= WHOLE - ON_THE_PLANE ? high : low;
            return new Vector3f(at.x(), field.heightAt(at.x(), at.z()) * WHOLE, at.z());
        });
    }

    /**
     * A quad moved up or down whole, its texture kept.
     *
     * @param quad the quad
     * @param rise how far it moves up, in blocks, down where below 0
     * @return the moved quad
     */
    static BakedQuad risen(BakedQuad quad, float rise) {
        List<Vertex> moved = new ArrayList<>();
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            Vector3fc at = quad.position(vertex);
            moved.add(new Vertex(new Vector3f(at.x(), at.y() + rise, at.z()), quad.packedUV(vertex)));
        }
        return quadOf(moved, quad.direction(), quad.materialInfo());
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
        return quadOf(moved, flat.direction(), flat.materialInfo());
    }

    /**
     * @param vertices the quad's four vertices, in order
     * @param face     the side it draws as
     * @param material its material
     * @return the quad
     */
    private static BakedQuad quadOf(List<Vertex> vertices, Direction face, BakedQuad.MaterialInfo material) {
        Iterator<Vertex> inOrder = vertices.iterator();
        Vertex first = inOrder.next();
        Vertex second = inOrder.next();
        Vertex third = inOrder.next();
        Vertex fourth = inOrder.next();
        return new BakedQuad(first.position(), second.position(), third.position(), fourth.position(), first.uv(),
                second.uv(), third.uv(), fourth.uv(), face, material);
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
