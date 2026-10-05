package com.mercuriusxeno.goo.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that the window title ends in the handed-in branch in brackets, and comes
 * back as given when no branch, or a blank one, was handed in.
 */
class DevBranchTitleTest {

    private static final String TITLE = "Minecraft* 26.1 - Singleplayer";

    @Test
    void handedBranchEndsTheTitleInBrackets() {
        assertEquals(TITLE + " [feat/branch-in-dev-window-title]",
            DevBranchTitle.tagWithBranch(TITLE, "feat/branch-in-dev-window-title"));
    }

    @Test
    void handedBranchIsStrippedOfSurroundingWhitespace() {
        assertEquals(TITLE + " [26.1]", DevBranchTitle.tagWithBranch(TITLE, " 26.1\n"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n"})
    void missingOrBlankBranchLeavesTheTitleAsGiven(String branch) {
        assertEquals(TITLE, DevBranchTitle.tagWithBranch(TITLE, branch));
    }
}
