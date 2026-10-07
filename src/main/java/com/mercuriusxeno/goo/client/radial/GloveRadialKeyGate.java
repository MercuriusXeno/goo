package com.mercuriusxeno.goo.client.radial;

import org.lwjgl.glfw.GLFW;

/**
 * The glove menu key as pure decisions: a press with a glove held and no
 * screen open opens the radial, and a release selects the hovered ability
 * and closes, or closes with the glove unchanged over nothing or while the
 * petals still move.
 * decision g-opens-radial-while-glove-held
 * decision radial-selects-on-g-release
 * decision mid-animation-input-does-nothing
 */
public final class GloveRadialKeyGate {

    /** The glove menu mapping's translation key. */
    public static final String NAME_KEY = "key.goo.glove_menu";
    /** The path of the glove menu category's id, under the goo namespace. */
    public static final String CATEGORY_PATH = "glove";
    /** The glove menu mapping's default key. */
    public static final int DEFAULT_KEY = GLFW.GLFW_KEY_G;

    /** What a release of the key does to the open radial. */
    public interface ReleaseActions {

        /**
         * Writes the hovered ability to the glove.
         *
         * @param hovered the wheel's outcome naming the type and ability
         */
        void selectHovered(RadialWheel.Outcome hovered);

        /** Closes the radial. */
        void close();
    }

    private GloveRadialKeyGate() {}

    /**
     * Whether a press of the glove menu key opens the radial.
     *
     * @param gloveHeld  whether either hand holds a glove
     * @param screenOpen whether a screen is open
     * @return true when the press opens the radial
     */
    public static boolean pressOpensRadial(boolean gloveHeld, boolean screenOpen) {
        return gloveHeld && !screenOpen;
    }

    /**
     * The pick a release or a click reads: nothing while the petals still
     * move, since where they land is not yet what the player sees, and the
     * hovered ability once they have landed. A mouse click selects nothing in
     * the radial either way.
     * decision mid-animation-input-does-nothing
     *
     * @param animating whether the petals' ease is still running
     * @param hovered   the wheel's outcome, from {@link RadialWheel#click()}
     * @return the cancel while animating, else the hovered outcome
     */
    public static RadialWheel.Outcome settledPick(boolean animating, RadialWheel.Outcome hovered) {
        return animating ? RadialWheel.Outcome.CANCEL : hovered;
    }

    /**
     * Resolves a release of the glove menu key while the radial is open.
     *
     * @param hovered the wheel's outcome at the release, from {@link RadialWheel#click()}
     * @param actions the selection and close the release resolves to
     */
    public static void release(RadialWheel.Outcome hovered, ReleaseActions actions) {
        if (hovered.selects()) {
            actions.selectHovered(hovered);
        }
        actions.close();
    }
}
