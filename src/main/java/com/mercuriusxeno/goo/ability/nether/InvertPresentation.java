package com.mercuriusxeno.goo.ability.nether;

/**
 * How an inverted player is shown. Its own first person camera rolls half a
 * turn with its body, so the world reads as if it stood right side up on its
 * ceiling, and the mouse and the strafe keys turn and move it toward the
 * sides it sees; its model draws upside down, feet on the ceiling, in third
 * person and to every other player.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 *
 * @param cameraRoll      the degrees the player's own camera rolls by
 * @param modelUpsideDown whether the player's model draws upside down
 * @param steerSign       the sign the mouse's turn and the strafe keys take, -1 while the camera is rolled
 */
public record InvertPresentation(float cameraRoll, boolean modelUpsideDown, float steerSign) {

    /** An upright player's presentation. */
    public static final InvertPresentation UPRIGHT = new InvertPresentation(0F, false, 1F);
    /** Half a turn, the roll that sets an inverted body's view right side up. */
    private static final float HALF_TURN = 180F;
    /** An inverted player seen through its own eyes. */
    private static final InvertPresentation ROLLED = new InvertPresentation(HALF_TURN, true, -1F);
    /** An inverted player seen from outside its eyes. */
    private static final InvertPresentation FLIPPED = new InvertPresentation(0F, true, 1F);
    /**
     * Twice the eighth of a block the player renderer drops a crouching model
     * by: once to take the drop back and once to raise the model by it.
     */
    private static final float CROUCH_DROP_TAKEN_BACK_AND_RAISED = 4F / 16F;

    /**
     * The presentation of a player.
     *
     * @param inverted    whether Invert turns the player's gravity over
     * @param firstPerson whether the player's own camera sits in its eyes
     * @return how the player is shown
     */
    public static InvertPresentation of(boolean inverted, boolean firstPerson) {
        if (!inverted) {
            return UPRIGHT;
        }
        return firstPerson ? ROLLED : FLIPPED;
    }

    /**
     * The world height a crouching model's drawing moves by on top of the
     * player renderer's crouch offset, which drops every crouching model an
     * eighth of a block; an upside-down model takes twice that back, so its
     * crouch rises toward its ceiling by the eighth.
     *
     * @param upsideDown whether the model draws upside down
     * @param crouching  whether the player crouches
     * @param scale      the model's scale
     * @return the height added to the render offset
     */
    public static float crouchCorrection(boolean upsideDown, boolean crouching, float scale) {
        return upsideDown && crouching ? CROUCH_DROP_TAKEN_BACK_AND_RAISED * scale : 0F;
    }

}
