package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.data.GooConstantParser;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Splits an expression into text and $name or $name.type links, each link naming the variable it opens, with the
 * scan the loader reads constants by (decision references-link-to-their-variable-and-hover-a-card).
 */
public final class ExpressionReferences {
    private static final char REFERENCE_MARK = '$';
    private static final char TYPE_MARK = '.';

    private ExpressionReferences() {
    }

    /**
     * One run of an expression as the tab draws it.
     *
     * @param label    the run's text as the expression spells it
     * @param variable the variable a link opens, null for plain text
     */
    public record Node(String label, @Nullable String variable) {
        /**
         * @return whether the run is a link to a variable
         */
        public boolean link() {
            return variable != null;
        }
    }

    /**
     * @param text the expression
     * @return its runs in order, adjacent plain text joined into one run
     */
    public static List<Node> nodes(String text) {
        List<Node> nodes = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int at = 0;
        while (at < text.length()) {
            int nameEnd = text.charAt(at) == REFERENCE_MARK ? GooConstantParser.scanIdentifier(text, at + 1) : at;
            if (nameEnd == at || nameEnd == at + 1) {
                plain.append(text.charAt(at));
                at++;
                continue;
            }
            int end = typeSuffixEnd(text, nameEnd);
            if (!plain.isEmpty()) {
                nodes.add(new Node(plain.toString(), null));
                plain.setLength(0);
            }
            nodes.add(new Node(text.substring(at, end), text.substring(at + 1, nameEnd)));
            at = end;
        }
        if (!plain.isEmpty()) {
            nodes.add(new Node(plain.toString(), null));
        }
        return nodes;
    }

    /**
     * @param text the expression
     * @return each variable the expression reads, once, in order
     */
    public static List<String> names(String text) {
        Set<String> names = new LinkedHashSet<>();
        nodes(text).stream().filter(Node::link).forEach(node -> names.add(node.variable()));
        return List.copyOf(names);
    }

    private static int typeSuffixEnd(String text, int nameEnd) {
        if (nameEnd < text.length() && text.charAt(nameEnd) == TYPE_MARK) {
            int typeEnd = GooConstantParser.scanIdentifier(text, nameEnd + 1);
            if (typeEnd > nameEnd + 1) {
                return typeEnd;
            }
        }
        return nameEnd;
    }
}
