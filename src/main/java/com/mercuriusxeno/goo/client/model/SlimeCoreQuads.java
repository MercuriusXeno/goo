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
import java.util.Optional;

/**
 * Builds the quads a slime core's box draws as: an opaque box on the cell's
 * goo type's fluid sprite, untinted as the palm blob's core is, its six faces
 * baked as a block model's are and textured where the box stands in its
 * block. It reads no client state, so a test hands it any sprite.
 * connected-goo-draws-as-one-layered-mass-with-a-core
 * palm-blob-draws-as-the-charge-blob
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
     * @param box      the core's box, empty for a cell holding none
     * @param material the core's material: the goo type's fluid sprite, untinted, on the solid layer
     * @return the box's six faces, none for an empty box
     */
    public static List<BakedQuad> quadsOf(Optional<SlimeCoreBox> box, BakedQuad.MaterialInfo material) {
        if (box.isEmpty()) {
            return List.of();
        }
        SlimeCoreBox core = box.get();
        Vector3fc from = new Vector3f(core.minX(), core.minY(), core.minZ()).mul(PIXELS_PER_LAYER);
        Vector3fc to = new Vector3f(core.maxX(), core.maxY(), core.maxZ()).mul(PIXELS_PER_LAYER);
        List<BakedQuad> quads = new ArrayList<>(Direction.values().length);
        for (Direction face : Direction.values()) {
            quads.add(FaceBakery.bakeQuad(UNSHARED, from, to, FaceBakery.defaultFaceUV(from, to, face), Quadrant.R0,
                    material, face, BlockModelRotation.IDENTITY, null, ExtraFaceData.DEFAULT));
        }
        return quads;
    }
}
