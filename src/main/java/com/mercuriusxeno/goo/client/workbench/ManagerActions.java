package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.components.AbstractWidget;
import java.util.function.Consumer;

/**
 * What a manager pane asks of the screen holding it: a request to the server's workbench operations, a word that
 * the shared draft changed, a jump to a variable, and its widgets' place among the screen's
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
interface ManagerActions {
    /**
     * @param operation the workbench operation's name
     * @param text      the posted text
     * @param answered  takes the reply's text where the server answered; a refusal shows in the status line
     */
    void request(String operation, String text, Consumer<String> answered);

    /**
     * @param operation the workbench operation's name
     * @param text      the posted text
     * @param progress  takes each progress line the operation sends before it answers
     * @param answered  takes the reply's text where the server answered
     * @param refused   takes the refusal's text where it did not
     */
    void stream(String operation, String text, Consumer<String> progress, Consumer<String> answered,
                Consumer<String> refused);

    /**
     * The draft changed: the screen marks it unsaved and has every tab read it afresh.
     */
    void draftChanged();

    /**
     * The draft took an edit still being typed: the screen marks it unsaved and reads nothing afresh yet.
     */
    void markUnsaved();

    /**
     * The server saved the draft itself, as a dump run does: the screen reads it back and has every tab read it.
     */
    void reloadDraft();

    /**
     * Opens the Variables tab on a variable, as a $name link does.
     *
     * @param name the variable
     */
    void openVariable(String name);

    /**
     * @param widget a widget the pane draws and the screen routes input to
     * @param <W>    the widget's type
     * @return the widget
     */
    <W extends AbstractWidget> W addWidget(W widget);

    /**
     * @param widget the widget taking keyboard focus
     */
    void focus(AbstractWidget widget);
}
