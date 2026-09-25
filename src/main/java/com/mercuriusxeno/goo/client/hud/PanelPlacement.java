package com.mercuriusxeno.goo.client.hud;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where and how a HUD panel stands in the world: its world anchor, the way it
 * turns toward the camera, the face it hangs from, and the animated pitch.
 *
 * @param anchor the panel anchor in world coordinates
 * @param facing how the panel turns toward the camera
 * @param face   the block face the panel stands on; DOWN hangs the panel below its anchor
 * @param pitch  the billboard pitch factor [0, 1]
 */
public record PanelPlacement(Vec3 anchor, Facing facing, Direction face, float pitch) {

    /** Offset along the panel normal that lifts a top or bottom panel off the block face. */
    private static final float VERTICAL_FACE_NUDGE = 0.01f;

    /** Offset along the panel normal for a side-face or rim panel. */
    private static final float SIDE_FACE_NUDGE = -0.01f;

    /**
     * Places a panel on a block face: side faces lie flat against the face, top and
     * bottom faces billboard toward the camera, or lie flat when a block sits above.
     *
     * @param anchor    the panel anchor in world coordinates
     * @param face      the block face the panel stands on
     * @param flatOnTop whether a top or bottom panel lies flat instead of billboarding
     * @param pitch     the billboard pitch factor [0, 1]
     * @return the placement
     */
    public static PanelPlacement onFace(Vec3 anchor, Direction face, boolean flatOnTop, float pitch) {
        boolean vertical = face == Direction.UP || face == Direction.DOWN;
        Facing facing = vertical ? (flatOnTop ? Facing.FLAT : Facing.BILLBOARD) : Facing.SIDE_FACE;
        return new PanelPlacement(anchor, facing, face, pitch);
    }

    /**
     * Places a billboarded panel on a basin rim.
     *
     * @param anchor the rim anchor in world coordinates
     * @param pitch  the billboard pitch factor [0, 1]
     * @return the placement
     */
    public static PanelPlacement onRim(Vec3 anchor, float pitch) {
        return new PanelPlacement(anchor, Facing.RIM, Direction.UP, pitch);
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
        RIM(SIDE_FACE_NUDGE);

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
