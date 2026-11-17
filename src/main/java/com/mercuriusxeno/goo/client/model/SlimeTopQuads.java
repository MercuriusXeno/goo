package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mojang.math.Quadrant;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
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

/**
 * Builds the quads a goo block's sloped top and its rims draw as, from the
 * heights {@link SlimeTop} reads: the top in patches whose corners stand at
 * the top's heights, and down each side no goo of its type continues past a
 * rim from the block's floor up to the top's edge. The patches split at the
 * slope's bends, 4 and 12 texels in, and at the core's margin, 2 and 14, so
 * each piece of the core's top stands under a piece of the shell of its own
 * extent and the translucent sort draws the core first from any view above.
 * Each face is textured where it stands in its block, as a block model's are.
 * goo-block-top-slopes-to-the-average-with-the-adjoining-block
 * slime-core-draws-translucent-at-the-shells-opacity
 */
public final class SlimeTopQuads {

    /** Where the top splits into patches along each axis, in model pixels. */
    static final float[] CUTS = {0f, 2f, 4f, 12f, 14f, 16f};
    /** Model pixels across a whole block. */
    private static final float WHOLE = 16f;
    /** The layers in a block's height. */
    private static final float LAYERS_TALL = VolumeCellBlock.MAX_LAYERS;

    private SlimeTopQuads() {
    }

    /**
     * The top's patches, none for a block goo of its type covers.
     *
     * @param top      the top's heights
     * @param material the shell's material: the goo type's fluid sprite, under the shell tint, translucent
     * @return one quad for each patch
     */
    public static List<BakedQuad> topOf(SlimeTop top, BakedQuad.MaterialInfo material) {
        List<BakedQuad> quads = new ArrayList<>();
        if (top.covered()) {
            return quads;
        }
        for (int x = 0; x + 1 < CUTS.length; x++) {
            for (int z = 0; z + 1 < CUTS.length; z++) {
                Vector3fc from = new Vector3f(CUTS[x], 0, CUTS[z]);
                Vector3fc to = new Vector3f(CUTS[x + 1], 0, CUTS[z + 1]);
                quads.add(standOn(faceOf(from, to, Direction.UP, material), top, false));
            }
        }
        return quads;
    }

    /**
     * The rims down each side no goo of the block's type continues past,
     * keyed by the side, so each culls against a neighbour hiding it as a
     * block model's side face does.
     *
     * @param top      the top's heights and rims
     * @param material the shell's material
     * @return each rim side's quads, one for each patch along its edge
     */
    public static Map<Direction, List<BakedQuad>> rimsOf(SlimeTop top, BakedQuad.MaterialInfo material) {
        Map<Direction, List<BakedQuad>> rims = new EnumMap<>(Direction.class);
        for (Direction side : top.rims()) {
            List<BakedQuad> quads = new ArrayList<>();
            for (int along = 0; along + 1 < CUTS.length; along++) {
                Vector3fc from = rimCorner(side, CUTS[along], 0);
                Vector3fc to = rimCorner(side, CUTS[along + 1], WHOLE);
                BakedQuad rim = standOn(faceOf(from, to, side, material), top, true);
                if (tallestOf(rim) > 0) {
                    quads.add(rim);
                }
            }
            rims.put(side, quads);
        }
        return rims;
    }

    /**
     * A corner of a rim on a side's plane.
     *
     * @param side   the rim's side
     * @param along  where the corner stands along the side, in model pixels
     * @param height its height, in model pixels
     * @return the corner, in model pixels
     */
    private static Vector3fc rimCorner(Direction side, float along, float height) {
        float plane = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? WHOLE : 0;
        return side.getAxis() == Direction.Axis.X ? new Vector3f(plane, height, along)
                : new Vector3f(along, height, plane);
    }

    private static BakedQuad faceOf(Vector3fc from, Vector3fc to, Direction face, BakedQuad.MaterialInfo material) {
        return FaceBakery.bakeQuad(SlimeCoreQuads.UNSHARED, from, to, FaceBakery.defaultFaceUV(from, to, face),
                Quadrant.R0, material, face, BlockModelRotation.IDENTITY, null, ExtraFaceData.DEFAULT);
    }

    /**
     * A flat quad with its vertices raised to the top's heights: every
     * vertex of a patch, or a rim's upper vertices, its lower ones kept on
     * the floor and its texture following each vertex's height.
     *
     * @param flat the quad baked flat
     * @param top  the top's heights
     * @param rim  true for a rim, false for a patch of the top
     * @return the quad standing on the top
     */
    private static BakedQuad standOn(BakedQuad flat, SlimeTop top, boolean rim) {
        List<Vertex> raised = new ArrayList<>();
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            Vector3fc at = flat.position(vertex);
            float height = (float) top.heightAtPixel(at.x() * WHOLE, at.z() * WHOLE) / LAYERS_TALL;
            float y = !rim || at.y() > 0 ? height : 0;
            long uv = rim ? UVPair.pack(UVPair.unpackU(flat.packedUV(vertex)),
                    flat.materialInfo().sprite().getV(Math.clamp(1 - y, 0f, 1f))) : flat.packedUV(vertex);
            raised.add(new Vertex(new Vector3f(at.x(), y, at.z()), uv));
        }
        Iterator<Vertex> inOrder = raised.iterator();
        Vertex first = inOrder.next();
        Vertex second = inOrder.next();
        Vertex third = inOrder.next();
        Vertex fourth = inOrder.next();
        return new BakedQuad(first.position(), second.position(), third.position(), fourth.position(), first.uv(),
                second.uv(), third.uv(), fourth.uv(), flat.direction(), flat.materialInfo());
    }

    /**
     * One vertex of a quad standing on the top.
     *
     * @param position where it stands, in block units
     * @param uv       its packed texture coordinate
     */
    private record Vertex(Vector3fc position, long uv) {
    }

    private static float tallestOf(BakedQuad quad) {
        float tallest = 0;
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            tallest = Math.max(tallest, quad.position(vertex).y());
        }
        return tallest;
    }
}
