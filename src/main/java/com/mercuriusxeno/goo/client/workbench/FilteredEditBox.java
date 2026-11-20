package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import java.util.function.Predicate;

/**
 * An edit box that takes only the text its filter accepts, as NeoForge's
 * EditBox filter did on 26.1 before 26.2 dropped it: a value set whole is
 * tested whole, and typed or pasted text is tested as it would be inserted.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
final class FilteredEditBox extends EditBox {

    private Predicate<String> filter = text -> true;

    /**
     * @param font      the font the box draws its text in
     * @param x         the box's left edge
     * @param y         the box's top edge
     * @param width     the box's width
     * @param height    the box's height
     * @param narration what the box narrates
     */
    FilteredEditBox(Font font, int x, int y, int width, int height, Component narration) {
        super(font, x, y, width, height, narration);
    }

    /**
     * Sets the test text must pass to enter the box.
     *
     * @param filter the test
     */
    void setFilter(Predicate<String> filter) {
        this.filter = filter;
    }

    @Override
    public void setValue(String value) {
        if (filter.test(value)) {
            super.setValue(value);
        }
    }

    @Override
    public void insertText(String input) {
        if (filter.test(StringUtil.filterText(input))) {
            super.insertText(input);
        }
    }
}
