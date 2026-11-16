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
 * Builds the quads a slime core's boxes draw as: a body at the shell's alpha
 * on the cell's goo type's fluid sprite, in its own colors as the palm blob's core is, each
 * face baked as a block model's are and textured where it stands in its
 * block, so the texture runs on across a join as across tiled blocks. A face
 * on the block's own boundary draws nothing where the core across covers it,
 * beside it or above and below in a column. It reads no client state, so a
 * test hands it any sprite. A cell of any fill draws a core, down to a
 * sliver at one layer.
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
    private static final ModelBaker.Interner UNSHARED = new ModelBaker.Interner() {
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
        for (SlimeCoreBox box : SlimeCoreBox.boxesOf(around)) {
            Vector3fc from = new Vector3f(box.minX(), box.minY(), box.minZ()).mul(PIXELS_PER_UNIT);
            Vector3fc to = new Vector3f(box.maxX(), box.maxY(), box.maxZ()).mul(PIXELS_PER_UNIT);
            for (Direction face : Direction.values()) {
                if (!box.onBoundary(face) || !box.joinsAcross(face, around)) {
                    quads.add(FaceBakery.bakeQuad(UNSHARED, from, to, FaceBakery.defaultFaceUV(from, to, face),
                            Quadrant.R0, material, face, BlockModelRotation.IDENTITY, null, ExtraFaceData.DEFAULT));
                }
            }
        }
        return quads;
    }
}