package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.block.ability.PrismColumn;
import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.client.PrismCrystal;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * The prism's quartz column part way into the oculus's eye, in model pixels
 * on {@link CrystalCluster}'s base point along +y, as the prism's own morph
 * draws the blob into the column: each point of the column slides to its
 * place on a small six-sided lens floating where the eye hovers. The base
 * lifts off the face, the shaft draws in, and the point squats into the
 * lens's upper cap, so the column itself becomes the eye.
 * Decisions oculus-prism-becomes-a-hovering-eye and model-transformation-is-one-animation.
 */
public final class OculusMorph {

    /** Where the eye's middle hovers off the face, in model pixels. */
    static final double EYE_LIFT = 3.5;
    /** The eye's radius, in model pixels. */
    static final double EYE_RADIUS = 3;
    /** How far the lens's rims stand from its middle, a share of its radius. */
    private static final double RIM_SHARE = 0.5;
    /** Points around the rim, every 15 degrees, so the hexagon's corners fall on one. */
    private static final int RIM_SLICES = 24;
    private static final double HEXAGON_SIDE = Math.PI / 3;
    private static final double HALF_TURN = 0.5;
    private static final int BASE_CENTER = 0;
    private static final int BOTTOM_RIM = 1;
    private static final int TOP_RIM = 2;
    private static final int TOP_CENTER = 3;
    private static final int RINGS = TOP_CENTER + 1;

    private OculusMorph() {
    }

    /**
     * The column part way into the eye's lens.
     *
     * @param morph how far the column has become the lens, 0 for the column, 1 for the lens
     * @return the faces, each four corners wound outward
     */
    public static List<Vec3[]> faces(double morph) {
        CrystalCluster.Prism column = PrismColumn.PRISM;
        double shaft = column.length() - column.tipLength();
        double lensRim = EYE_RADIUS * RIM_SHARE;
        Vec3[][] rings = new Vec3[RINGS][RIM_SLICES + 1];
        for (int slice = 0; slice <= RIM_SLICES; slice++) {
            double angle = Math.TAU * slice / RIM_SLICES;
            double hexagon = rimShare(angle);
            double columnRim = column.radius() * hexagon;
            double eyeRim = EYE_RADIUS * hexagon;
            rings[BASE_CENTER][slice] = point(angle, 0, 0, 0, EYE_LIFT - EYE_RADIUS, morph);
            rings[BOTTOM_RIM][slice] = point(angle, columnRim, 0, eyeRim, EYE_LIFT - lensRim, morph);
            rings[TOP_RIM][slice] = point(angle, columnRim, shaft, eyeRim, EYE_LIFT + lensRim, morph);
            rings[TOP_CENTER][slice] = point(angle, 0, column.length(), 0, EYE_LIFT + EYE_RADIUS, morph);
        }
        return PrismCrystal.facesBetween(rings);
    }

    /**
     * The hexagon's rim at an angle, as a share of its corner radius.
     *
     * @param angle the angle around the axis
     * @return the share, 1 at a corner
     */
    private static double rimShare(double angle) {
        double half = HEXAGON_SIDE * HALF_TURN;
        double fromCorner = (angle % HEXAGON_SIDE + HEXAGON_SIDE) % HEXAGON_SIDE;
        return Math.cos(half) / Math.cos(fromCorner - half);
    }

    /**
     * @param angle        the angle around the axis
     * @param columnRim    the point's distance from the axis on the column
     * @param columnHeight its height up the axis on the column
     * @param eyeRim       its distance from the axis on the lens
     * @param eyeHeight    its height up the axis on the lens
     * @param morph        how far the column has become the lens
     * @return the point, in model pixels
     */
    private static Vec3 point(double angle, double columnRim, double columnHeight, double eyeRim, double eyeHeight,
                              double morph) {
        double rim = columnRim + (eyeRim - columnRim) * morph;
        double height = columnHeight + (eyeHeight - columnHeight) * morph;
        return new Vec3(CrystalCluster.BASE_X + Math.cos(angle) * rim, CrystalCluster.BASE_Y + height,
                CrystalCluster.BASE_Z - Math.sin(angle) * rim);
    }
}
