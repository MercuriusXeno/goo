package com.mercuriusxeno.goo.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Breaks a text into lines no wider than a width at its spaces, which the radial wheel's explanation panel and the
 * values manager's Audit tab both draw.
 */
public final class WordWrap {
    private static final String WORD_SEPARATOR = " ";

    private WordWrap() {
    }

    /**
     * Breaks a text into lines no wider than a width, word by word; a word wider than the width stands on a line of
     * its own.
     *
     * @param text    the text
     * @param width   the widest a line runs
     * @param widthOf the width in pixels the font gives a string
     * @return the lines, top first
     */
    public static List<String> lines(String text, int width, ToIntFunction<String> widthOf) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split(WORD_SEPARATOR)) {
            String joined = line.isEmpty() ? word : line + WORD_SEPARATOR + word;
            if (!line.isEmpty() && widthOf.applyAsInt(joined) > width) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(joined);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
