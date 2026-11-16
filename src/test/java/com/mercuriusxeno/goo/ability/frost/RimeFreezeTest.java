package com.mercuriusxeno.goo.ability.frost;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What Rime freezes a block to: a water source to Rime ice and a lava source
 * to obsidian, and nothing for what already stands frozen, the change running
 * one way, warm to frozen
 * (decisions rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless,
 * rime-freezing-is-permanent-with-no-restore-on-removal).
 */
class RimeFreezeTest {

    @Test
    void waterFreezesToRimeIce() {
        assertEquals(Optional.of(RimeFreeze.ICE), RimeFreeze.WATER.freeze());
    }

    @Test
    void lavaFreezesToObsidian() {
        assertEquals(Optional.of(RimeFreeze.OBSIDIAN), RimeFreeze.LAVA.freeze());
    }

    @Test
    void rimeIceAndObsidianFreezeToNothing() {
        assertEquals(List.of(Optional.empty(), Optional.empty()),
                List.of(RimeFreeze.ICE.freeze(), RimeFreeze.OBSIDIAN.freeze()));
    }
}
