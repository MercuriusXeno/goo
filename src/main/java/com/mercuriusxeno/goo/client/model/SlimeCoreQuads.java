package com.mercuriusxeno.goo.client.model;

import com.mojang.math.Quadrant;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the quads a slime core's boxes draw as: a body at 50% opacity
 * on the cell's goo type's fluid sprite, in its own colors as the palm blob's core is, each
 * face baked as a block model's are and textured where it stands in its
 * block, so the texture runs on across a join as across tiled blocks. A face
 * on the block's own boundary draws nothing where the core across covers it,
 * beside it or above and below in a column, and a face inside the block
 * draws nothing where another of the cell's own boxes stands against it. It
 * reads no client state, so a test hands it any sprite. A cell of any fill
 * draws a core, down to a sliver at one layer.
 * connected-goo-draws-as-one-layered-mass-with-a-core
 * palm-blob-draws-as-the-charge-blob
 * residual-always-draws-a-core-inside-a-one-eighth-margin
 * slime-cores-connect-where-only-padding-parts-them
 * stacked-goo-cells-share-one-slime-core
 */
public final class SlimeCoreQuads {

    /** Model pixels to one of the core box's units, a 120th of a block. */
    private static final float PIXELS_PER_UNIT = 16f / SlimeCoreBox.WHOLE;
    /** Bakes each quad as it comes, sharing nothing between them. */
    static final ModelBaker.Interner UNSHARED = new ModelBaker.Interner() {
        @Override
        public Vector3fc vector(Vector3fc vector) {
            return vector;
        }

        @Override
        public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) {
            return material;
        }
    };

    private SlimeCoreQuads() {
    }

    /**
     * The quads a cell's core draws as.
     *
     * @param around   the layers the cells of the goo hold around the cell
     * @param material the core's material: the goo type's fluid sprite, on the translucent layer
     * @return each core box's faces but those the core across covers, none for a cell of no layers
     */
    public static List<BakedQuad> quadsOf(LayersAround around, BakedQuad.MaterialInfo material) {
        List<BakedQuad> quads = new ArrayList<>();
        List<SlimeCoreBox> core = SlimeCoreBox.boxesOf(around);
        for (SlimeCoreBox box : core) {
            for (Direction face : Direction.values()) {
                for (SlimeCoreBox shown : shownParts(box, face, around, core)) {
                    quads.add(faceOf(shown, face, material));
                }
            }
        }
        return quads;
    }

    /**
     * The parts of a box whose side draws: a side on its block's boundary
     * draws whole unless the core across covers it, and a side inside the
     * block draws only where no other box of the cell's core stands against it.
     *
     * @param box    one box of the cell's core
     * @param face   the side drawn
     * @param around the layers the cells of the goo hold around the cell
     * @param core   every box of the cell's core
     * @return the parts of the box whose side draws
     */
    private static List<SlimeCoreBox> shownParts(SlimeCoreBox box, Direction face, LayersAround around,
                                                 List<SlimeCoreBox> core) {
        if (box.onBoundary(face)) {
            return box.joinsAcross(face, around) ? List.of() : List.of(box);
        }
        return box.exposedAlong(face, core);
    }

    /**
     * One side of a box as a block model bakes it, textured where it stands in its block.
     *
     * @param box      the box, in the core box's units
     * @param face     the side
     * @param material the core's material
     * @return the side's quad
     */
    /**
     * A shell's top cut into the bands a core's plan splits into, so each
     * core top piece stands under a shell piece of its own extent and the
     * translucent sort, farthest centre first, draws the core before the
     * shell over it from any view above.
     * slime-core-draws-translucent-at-the-shells-opacity
     *
     * @param top the shell's top, spanning its whole block
     * @return the top's nine pieces, in its material
     */
    static List<BakedQuad> bandedTop(BakedQuad top) {
        float height = top.position(0).y() * SlimeCoreBox.WHOLE;
        int[] edges = SlimeCoreBox.BAND_EDGES;
        List<BakedQuad> pieces = new ArrayList<>();
        for (int x = 0; x + 1 < edges.length; x++) {
            for (int z = 0; z + 1 < edges.length; z++) {
                Vector3fc from = new Vector3f(edges[x], height, edges[z]).mul(PIXELS_PER_UNIT);
                Vector3fc to = new Vector3f(edges[x + 1], height, edges[z + 1]).mul(PIXELS_PER_UNIT);
                pieces.add(FaceBakery.bakeQuad(UNSHARED, from, to, FaceBakery.defaultFaceUV(from, to, Direction.UP),
                        Quadrant.R0, top.materialInfo(), Direction.UP, BlockModelRotation.IDENTITY, null,
                        ExtraFaceData.DEFAULT));
            }
        }
        return pieces;
    }

    static BakedQuad faceOf(SlimeCoreBox box, Direction face, BakedQuad.MaterialInfo material) {
        Vector3fc from = new Vector3f(box.minX(), box.minY(), box.minZ()).mul(PIXELS_PER_UNIT);
        Vector3fc to = new Vector3f(box.maxX(), box.maxY(), box.maxZ()).mul(PIXELS_PER_UNIT);
        return FaceBakery.bakeQuad(UNSHARED, from, to, FaceBakery.defaultFaceUV(from, to, face), Quadrant.R0,
                material, face, BlockModelRotation.IDENTITY, null, ExtraFaceData.DEFAULT);
    }
}