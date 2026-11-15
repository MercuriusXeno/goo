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
 * Builds the quads a slime core's boxes draw as: an opaque body on the
 * cell's goo type's fluid sprite, untinted as the palm blob's core is, each
 * face baked as a block model's are and textured where it stands in its
 * block, so the texture runs on across a join as across tiled blocks. A face
 * on the block's own boundary draws nothing, since a core reaches it only
 * where it joins the core across it. It reads no client state, so a test
 * hands it any sprite.
 * connected-goo-draws-as-one-layered-mass-with-a-core
 * palm-blob-draws-as-the-charge-blob
 * slime-cores-connect-where-only-padding-parts-them
 */
public final class SlimeCoreQuads {

    /** The tint index of a quad drawn in its sprite's own colors. */
    public static final int NO_TINT = -1;
    /** Model pixels to one fifteenth of a block. */
    private static final float PIXELS_PER_LAYER = 16f / SlimeCoreBox.WHOLE;
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
     * The quads a core draws as.
     *
     * @param boxes    the core's boxes, none for a cell holding no core
     * @param material the core's material: the goo type's fluid sprite, untinted, on the solid layer
     * @return each box's faces but those on its block's boundary
     */
    public static List<BakedQuad> quadsOf(List<SlimeCoreBox> boxes, BakedQuad.MaterialInfo material) {
        List<BakedQuad> quads = new ArrayList<>();
        for (SlimeCoreBox box : boxes) {
            Vector3fc from = new Vector3f(box.minX(), box.minY(), box.minZ()).mul(PIXELS_PER_LAYER);
            Vector3fc to = new Vector3f(box.maxX(), box.maxY(), box.maxZ()).mul(PIXELS_PER_LAYER);
            for (Direction face : Direction.values()) {
                if (!onBoundary(box, face)) {
                    quads.add(FaceBakery.bakeQuad(UNSHARED, from, to, FaceBakery.defaultFaceUV(from, to, face),
                            Quadrant.R0, material, face, BlockModelRotation.IDENTITY, null, ExtraFaceData.DEFAULT));
                }
            }
        }
        return quads;
    }

    /**
     * Whether a box's face lies on its block's boundary, the face it shares with the core it joins.
     *
     * @param box  the box
     * @param face the side of the box
     * @return true where that side stands on the block's own face
     */
    private static boolean onBoundary(SlimeCoreBox box, Direction face) {
        Direction.Axis axis = face.getAxis();
        if (face.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            return axis.choose(box.maxX(), box.maxY(), box.maxZ()) == SlimeCoreBox.WHOLE;
        }
        return axis.choose(box.minX(), box.minY(), box.minZ()) == 0;
    }
}