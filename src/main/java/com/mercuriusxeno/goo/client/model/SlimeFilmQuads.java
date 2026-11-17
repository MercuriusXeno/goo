package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.GooShares;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
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
    private static final float ONE_LAYER = WHOLE / VolumeCellBlock.MAX_LAYERS;
    /** How far a streak stands out from its wall, in model pixels: the edge drips' hair, clear of the wall. */
    private static final float STREAK_OUT = 0.1f;
    /** Each strip of a streak, its span across the wall in model pixels. */
    private static final float[][] STRIPS = {{3f, 4.5f}, {7.5f, 9f}, {12f, 13.5f}};

    private SlimeFilmQuads() {
    }

    /**
     * The quads a block's attached goo draws as: each attached side's share
     * of its volume as a slab standing out from that wall by that share, and
     * the floor's share as a fill on its floor.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     *
     * @param shares   how the block's volume divides between its surfaces
     * @param material the goo's material: the goo type's fluid sprite, under the shell tint, translucent
     * @return two faces for each side's slab, and the floor fill's top and sides
     */
    public static List<BakedQuad> quadsOf(GooShares shares, BakedQuad.MaterialInfo material) {
        List<BakedQuad> quads = new ArrayList<>();
        shares.sides().forEach((wall, share) -> {
            float depth = (float) (share * ONE_LAYER);
            Vector3fc from = slabCorner(wall, false, depth);
            Vector3fc to = slabCorner(wall, true, depth);
            for (Direction face : List.of(wall, wall.getOpposite())) {
                quads.add(faceOf(from, to, face, material));
            }
        });
        if (shares.floor() > 0) {
            Vector3fc from = new Vector3f(0, 0, 0);
            Vector3fc to = new Vector3f(WHOLE, (float) (shares.floor() * ONE_LAYER), WHOLE);
            for (Direction face : List.of(Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH,
                    Direction.WEST)) {
                quads.add(faceOf(from, to, face, material));
            }
        }
        return quads;
    }

    private static BakedQuad faceOf(Vector3fc from, Vector3fc to, Direction face, BakedQuad.MaterialInfo material) {
        return FaceBakery.bakeQuad(SlimeCoreQuads.UNSHARED, from, to, FaceBakery.defaultFaceUV(from, to, face),
                Quadrant.R0, material, face, BlockModelRotation.IDENTITY, null, ExtraFaceData.DEFAULT);
    }

    /**
     * The quads a cell's runny layers draw as: each a streak of strips
     * running the height of its wall, flat on the wall's plane a hair out
     * from it as the edge drips stand, facing the open cell, in the drips'
     * texture, the goo type's fluid sprite under the shell tint.
     * runny-goo-runs-down-the-wall-to-the-first-top-below
     *
     * @param walls    the sides holding a runny layer
     * @param material the streak's material, the film's
     * @return one face for each strip of each streak
     */
    public static List<BakedQuad> streaksOf(List<Direction> walls, BakedQuad.MaterialInfo material) {
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction wall : walls) {
            boolean wallOnHigh = wall.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            float depth = wallOnHigh ? WHOLE - STREAK_OUT : STREAK_OUT;
            for (float[] strip : STRIPS) {
                Vector3fc from = wall.getAxis() == Direction.Axis.X ? new Vector3f(depth, 0, strip[0])
                        : new Vector3f(strip[0], 0, depth);
                Vector3fc to = wall.getAxis() == Direction.Axis.X ? new Vector3f(depth, WHOLE, strip[1])
                        : new Vector3f(strip[1], WHOLE, depth);
                Direction out = wall.getOpposite();
                quads.add(FaceBakery.bakeQuad(SlimeCoreQuads.UNSHARED, from, to,
                        FaceBakery.defaultFaceUV(from, to, out), Quadrant.R0, material, out,
                        BlockModelRotation.IDENTITY, null, ExtraFaceData.DEFAULT));
            }
        }
        return quads;
    }

    /**
     * A corner of an attached side's slab, in model pixels: the slab spans
     * the whole wall and its depth out from it.
     *
     * @param wall the side whose wall the goo clings to
     * @param high the corner toward positive x, y and z, else the one toward negative
     * @param slab the slab's depth from the wall, in model pixels
     * @return the corner
     */
    private static Vector3fc slabCorner(Direction wall, boolean high, float slab) {
        float across = high ? WHOLE : 0f;
        float depth = depthAlong(wall, high, slab);
        return switch (wall.getAxis()) {
            case X -> new Vector3f(depth, across, across);
            case Y -> new Vector3f(across, depth, across);
            case Z -> new Vector3f(across, across, depth);
        };
    }

    /**
     * Where a slab's corner stands along its wall's axis, in model pixels.
     *
     * @param wall the side whose wall the goo clings to
     * @param high the corner toward the positive end of the axis, else the one toward the negative
     * @param slab the slab's depth from the wall, in model pixels
     * @return the corner's coordinate on the wall's axis
     */
    private static float depthAlong(Direction wall, boolean high, float slab) {
        if (wall.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            return high ? WHOLE : WHOLE - slab;
        }
        return high ? slab : 0f;
    }
}
