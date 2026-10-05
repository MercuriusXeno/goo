package com.mercuriusxeno.goo.client;

/**
 * Appends the dev run's git branch to the client window title, so each running dev
 * client names the worktree it came from; a client handed no branch keeps its title.
 */
public final class DevBranchTitle {

    // Only the dev client run sets this property, so a release jar's title is untouched
    // (decision dev-window-title-carries-the-branch).
    private static final String BRANCH_PROPERTY = "goo.devBranch";
    private static final String BRANCH_OPEN = " [";
    private static final String BRANCH_CLOSE = "]";

    private DevBranchTitle() {
    }

    /**
     * @param title the title the game built
     * @return the title tagged with the branch the dev run handed in, or the title as given
     */
    public static String tagWithDevBranch(String title) {
        return tagWithBranch(title, System.getProperty(BRANCH_PROPERTY));
    }

    /**
     * @param title  the title the game built
     * @param branch the branch name, null or blank when none was handed in
     * @return the title with the branch in brackets at its end, or the title as given
     */
    static String tagWithBranch(String title, String branch) {
        if (branch == null || branch.isBlank()) {
            return title;
        }
        return title + BRANCH_OPEN + branch.strip() + BRANCH_CLOSE;
    }
}
