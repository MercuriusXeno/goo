package com.mercuriusxeno.goo.client.model;

import com.mojang.math.Quadrant;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the quads a volume cell's films draw as: each a slab one layer
 * thick against its wall, its face out one layer from the wall facing the
 * open cell and its face on the wall's plane facing the wall, in the goo's
 * layer texture under the shell tint. The slab's edges lie on the block's
 * boundary, where the floor and the walls beside draw, so it draws none.
 * goo-cell-holds-a-fill-and-a-state-per-side
 * drip-striking-a-side-sticks-or-runs-by-adhesion
 */
public final class SlimeFilmQuads {

    /** Model pixels across a whole block. */
    private static final float WHOLE = 16f;
    /** Model pixels to one fifteenth of a block, a film's thickness. */
    private static final float ONE_LAYER = WHOLE / SlimeCoreBox.WHOLE;

    private SlimeFilmQuads() {
    }

    /**
     * The quads a cell's films draw as.
     *
     * @param walls    the sides holding a film
     * @param material the film's material: the goo type's fluid sprite, under the shell tint, translucent
     * @return two faces for each film
     */
    public static List<BakedQuad> quadsOf(List<Direction> walls, BakedQuad.MaterialInfo material) {
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction wall : walls) {
            Vector3fc from = slabCorner(wall, false);
            Vector3fc to = slabCorner(wall, true);
            for (Direction face : List.of(wall, wall.getOpposite())) {
                quads.add(FaceBakery.bakeQuad(SlimeCoreQuads.UNSHARED, from, to,
                        FaceBakery.defaultFaceUV(from, to, face), Quadrant.R0, material, face,
                        BlockModelRotation.IDENTITY, null, ExtraFaceData.DEFAULT));
            }
        }
        return quads;
    }

    /**
     * A corner of a film's slab, in model pixels: the slab spans the whole
     * wall and one layer out from it.
     *
     * @param wall the side whose wall the film clings to
     * @param high the corner toward positive x, y and z, else the one toward negative
     * @return the corner
     */
    private static Vector3fc slabCorner(Direction wall, boolean high) {
        boolean wallOnHigh = wall.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float across = high ? WHOLE : 0f;
        float depth;
        if (wallOnHigh) {
            depth = high ? WHOLE : WHOLE - ONE_LAYER;
        } else {
            depth = high ? ONE_LAYER : 0f;
        }
        return wall.getAxis() == Direction.Axis.X ? new Vector3f(depth, across, across)
                : new Vector3f(across, across, depth);
    }
}
