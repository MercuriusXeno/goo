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
 * Builds the quads a volume cell's runny layers draw as: each a streak of
 * strips running the height of its wall, flat on the wall's plane a hair
 * out from it, in the goo's fluid sprite under the shell tint. The goo a
 * block clings to a wall or a ceiling with draws through {@link SlimeTopQuads}.
 * runny-goo-runs-down-the-wall-to-the-first-top-below
 */
public final class SlimeFilmQuads {

    /** Model pixels across a whole block. */
    private static final float WHOLE = 16f;
    /** How far a streak stands out from its wall, in model pixels: the edge drips' hair, clear of the wall. */
    private static final float STREAK_OUT = 0.1f;
    /** Each strip of a streak, its span across the wall in model pixels. */
    private static final float[][] STRIPS = {{3f, 4.5f}, {7.5f, 9f}, {12f, 13.5f}};

    private SlimeFilmQuads() {
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
}
