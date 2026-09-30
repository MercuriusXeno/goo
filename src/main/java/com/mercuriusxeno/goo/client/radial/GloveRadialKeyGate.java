package com.mercuriusxeno.goo.client.radial;

/**
 * The glove menu key as pure decisions: a press with a glove held and no
 * screen open opens the radial, and a release selects the hovered ability
 * and closes, or closes with the glove unchanged over nothing.
 * decision g-opens-radial-while-glove-held
 * decision radial-selects-on-g-release
 */
public final class GloveRadialKeyGate {

    /** The glove menu mapping's translation key. */
    public static final String NAME_KEY = "key.goo.glove_menu";
    /** The path of the glove menu category's id, under the goo namespace. */
    public static final String CATEGORY_PATH = "glove";

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
