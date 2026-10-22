package com.mercuriusxeno.goo.client.hud;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where and how a HUD panel stands in the world: its world anchor, the way it
 * turns toward the camera, the face it hangs from, and the animated pitch and opacity.
 *
 * @param anchor the panel anchor in world coordinates
 * @param facing how the panel turns toward the camera
 * @param face   the block face the panel stands on
 * @param pitch   the billboard pitch factor [0, 1]
 * @param opacity the fade factor every part of the panel draws at [0, 1]
 * @param hangs   whether the anchor is the panel's top center and the panel hangs below it,
 *                rather than its bottom center with the panel standing above it
 */
public record PanelPlacement(Vec3 anchor, Facing facing, Direction face, float pitch, float opacity,
                             boolean hangs) {

    /** Offset along the panel normal that lifts a top or bottom panel off the block face. */
    private static final float VERTICAL_FACE_NUDGE = 0.01f;

    /**
     * Offset along the panel normal for a side-face panel: InWorldHud.faceRotation turns the
     * panel's +z onto the face's outward normal, so a positive nudge lifts the panel off the face.
     */
    private static final float SIDE_FACE_NUDGE = 0.01f;

    /** Offset along the panel normal for a rim panel. */
    private static final float RIM_NUDGE = -0.01f;

    /**
     * Places a panel on a block face: side faces lie flat against the face, top and
     * bottom faces billboard toward the camera, or lie flat when a block sits above.
     *
     * @param anchor    the panel anchor in world coordinates
     * @param face      the block face the panel stands on
     * @param flatOnTop whether a top or bottom panel lies flat instead of billboarding
     * @param pitch     the billboard pitch factor [0, 1]
     * @param opacity   the fade factor [0, 1]
     * @return the placement
     */
    public static PanelPlacement onFace(Vec3 anchor, Direction face, boolean flatOnTop,
                                        float pitch, float opacity) {
        boolean vertical = face == Direction.UP || face == Direction.DOWN;
        Facing facing = vertical ? (flatOnTop ? Facing.FLAT : Facing.BILLBOARD) : Facing.SIDE_FACE;
        return new PanelPlacement(anchor, facing, face, pitch, opacity, face == Direction.DOWN);
    }

    /**
     * Places a panel flat on a horizontal block face, hanging below its anchor, so the
     * panel grows downward as rows are added.
     *
     * @param anchor  the panel's top center in world coordinates
     * @param face    the horizontal block face the panel stands on
     * @param pitch   the billboard pitch factor [0, 1]
     * @param opacity the fade factor [0, 1]
     * @return the placement
     */
    public static PanelPlacement hangingOnFace(Vec3 anchor, Direction face, float pitch, float opacity) {
        return new PanelPlacement(anchor, Facing.SIDE_FACE, face, pitch, opacity, true);
    }

    /**
     * Places a billboarded panel on a basin rim.
     *
     * @param anchor the rim anchor in world coordinates
     * @param pitch   the billboard pitch factor [0, 1]
     * @param opacity the fade factor [0, 1]
     * @return the placement
     */
    public static PanelPlacement onRim(Vec3 anchor, float pitch, float opacity) {
        return new PanelPlacement(anchor, Facing.RIM, Direction.UP, pitch, opacity, false);
    }

    /**
     * How a panel turns toward the camera, and how far it nudges off its surface.
     */
    public enum Facing {
        /** Flat against a horizontal block face. */
        SIDE_FACE(SIDE_FACE_NUDGE),
        /** Above or below a block, turned toward the camera. */
        BILLBOARD(VERTICAL_FACE_NUDGE),
        /** Flat on the Y plane, turned toward the camera in yaw only. */
        FLAT(VERTICAL_FACE_NUDGE),
        /** On a basin rim, turned toward the camera. */
        RIM(RIM_NUDGE);

        /** Offset along the panel normal that keeps the panel off the surface it stands on. */
        private final float zNudge;

        /**
         * Binds a facing to its nudge.
         *
         * @param zNudge the offset along the panel normal
         */
        Facing(float zNudge) {
            this.zNudge = zNudge;
        }

        /**
         * Returns the offset along the panel normal.
         *
         * @return the z nudge in blocks
         */
        float zNudge() {
            return zNudge;
        }
    }
}
