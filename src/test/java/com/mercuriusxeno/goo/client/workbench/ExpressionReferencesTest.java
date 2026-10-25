package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the split of an expression into text and $name links, as the Variables tab and the hover card draw it
 * (decision references-link-to-their-variable-and-hover-a-card).
 */
class ExpressionReferencesTest {

    /**
     * $a and $b.metal each become a link naming its variable, the type suffix kept in the label, and the text
     * between stays text.
     */
    @Test
    void eachReferenceIsALinkNamingItsVariable() {
        List<ExpressionReferences.Node> nodes = ExpressionReferences.nodes("$a + $b.metal");
        assertEquals(List.of(
                new ExpressionReferences.Node("$a", "a"),
                new ExpressionReferences.Node(" + ", null),
                new ExpressionReferences.Node("$b.metal", "b")), nodes);
    }

    /**
     * Text with no reference stays one text node, and a lone $ links nothing.
     */
    @Test
    void textWithoutAReferenceStaysText() {
        assertEquals(List.of(new ExpressionReferences.Node("4 * stick", null)),
                ExpressionReferences.nodes("4 * stick"));
        assertEquals(List.of(new ExpressionReferences.Node("cost $", null)), ExpressionReferences.nodes("cost $"));
    }

    /**
     * The names an expression reads list each variable once, in order.
     */
    @Test
    void namesListEachVariableOnce() {
        assertEquals(List.of("base", "trait"), ExpressionReferences.names("$base * 2 + $trait.hex + $base"));
    }
}
