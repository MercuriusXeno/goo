package com.mercuriusxeno.goo.workbench;

import java.io.IOException;

/**
 * The text a draft starts from where no live file stands: an empty model in a fresh tree, the jar's
 * base_values.json in a world's datapack (decision goo-workbench-command-serves-the-page-from-the-game).
 */
@FunctionalInterface
public interface DraftSeed {

    /**
     * @return the seed's bytes
     * @throws IOException where the seed cannot be read
     */
    byte[] read() throws IOException;
}
