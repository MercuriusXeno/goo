package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;
import java.util.function.Consumer;

/**
 * The values manager's chrome: the title row's X, [?], Save, Revert and Promote and the tab row's five tabs, each the
 * panes' flat button frame taking its press through the frame's click areas, the active tab in the selected fill
 * (decision one-flat-painter-draws-every-button).
 */
final class ManagerChrome {
    /** The screen's side margin. */
    static final int MARGIN = 8;
    /** The title row's buttons' top. */
    static final int TOPBAR_Y = 3;
    /** The tab row's top. */
    static final int TAB_Y = 20;
    private static final int GAP = 2;
    private static final String SAVE = "Save";
    private static final String PROMOTE = "Promote";
    // decision topbar-discard-reads-revert
    private static final String REVERT = "Revert";
    private static final String CLOSE = "X";
    private static final String HELP = "?";

    private ManagerChrome() {
    }

    /**
     * What the chrome's buttons do.
     *
     * @param close   closes the manager (decision x-in-the-top-right-closes-the-manager)
     * @param help    toggles explainer mode (decision question-button-toggles-explainer-mode-with-the-drag-mask)
     * @param save    saves the draft
     * @param revert  asks the Revert confirm
     * @param promote asks the Promote confirm
     * @param pick    opens a tab
     */
    record Actions(Runnable close, Runnable help, Runnable save, Runnable revert, Runnable promote,
                   Consumer<ManagerTab> pick) {
    }

    /**
     * Where the title row's controls stand across the screen's width.
     *
     * @param closeLeft   the X's left edge
     * @param closeRight  the X's right edge, at the screen's right margin
     * @param helpLeft    the [?]'s left edge, the square one gap left of the X
     * @param topbarRight the right edge of the topbar's first button
     */
    record TitleRow(int closeLeft, int closeRight, int helpLeft, int topbarRight) {
    }

    /**
     * @param screenWidth the screen's width
     * @return the title row laid across it
     */
    static TitleRow titleRowOf(int screenWidth) {
        int closeRight = screenWidth - MARGIN;
        int closeLeft = closeRight - ButtonPainter.HEIGHT;
        int helpLeft = closeLeft - GAP - ButtonPainter.HEIGHT;
        return new TitleRow(closeLeft, closeRight, helpLeft, helpLeft - GAP);
    }

    /**
     * @return the topbar's labels in the order the title row lays them leftward from its right edge
     */
    static List<String> topbarLabels() {
        return List.of(SAVE, REVERT, PROMOTE);
    }

    /**
     * Draws the title row's buttons and the tab row, taking every press through the click areas.
     *
     * @param graphics    the frame's graphics
     * @param font        the font
     * @param targets     the frame's click areas
     * @param screenWidth the screen's width
     * @param active      the tab open now
     * @param actions     what each button does
     */
    static void draw(GuiGraphicsExtractor graphics, Font font, ClickTargets targets, int screenWidth, ManagerTab active,
                     Actions actions) {
        TitleRow row = titleRowOf(screenWidth);
        ButtonPainter.drawSquare(graphics, font, CLOSE, row.closeLeft(), TOPBAR_Y, targets, actions.close());
        ButtonPainter.drawSquare(graphics, font, HELP, row.helpLeft(), TOPBAR_Y, targets, actions.help());
        int right = row.topbarRight();
        for (String label : topbarLabels()) {
            int left = right - ButtonPainter.width(font, label);
            ButtonPainter.draw(graphics, font, label, left, TOPBAR_Y, ButtonPainter.HEIGHT, targets,
                    actionOf(label, actions), null);
            right = left - GAP;
        }
        for (ManagerTab each : ManagerTab.values()) {
            ButtonPainter.Box box = tabBox(font, each);
            Runnable pressed = () -> actions.pick().accept(each);
            if (each == active) {
                ButtonPainter.drawSelected(graphics, font, each.label(), box.left(), TAB_Y, targets, pressed);
            } else {
                ButtonPainter.draw(graphics, font, each.label(), box.left(), TAB_Y, ButtonPainter.HEIGHT, targets,
                        pressed, null);
            }
        }
    }

    private static Runnable actionOf(String label, Actions actions) {
        return switch (label) {
            case SAVE -> actions.save();
            case REVERT -> actions.revert();
            default -> actions.promote();
        };
    }

    /**
     * @param font the font
     * @param tab  a tab
     * @return where its button stands on the tab row, the tabs laid from the left margin a gap apart
     */
    static ButtonPainter.Box tabBox(Font font, ManagerTab tab) {
        int x = MARGIN;
        for (ManagerTab each : ManagerTab.values()) {
            int width = ButtonPainter.width(font, each.label());
            if (each == tab) {
                return new ButtonPainter.Box(x, TAB_Y, width, ButtonPainter.HEIGHT);
            }
            x += width + GAP;
        }
        throw new IllegalArgumentException(tab.name());
    }
}
