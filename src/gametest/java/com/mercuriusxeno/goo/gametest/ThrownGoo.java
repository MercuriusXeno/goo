package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.entity.RollingGoo;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Counts the goo a cast threw into a test's bay: every goo entity that
 * leaves the hand to carry an ability to its target, which a spontaneous
 * cast throws none of.
 * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
 */
public final class ThrownGoo {

    private ThrownGoo() {
    }

    /**
     * The thrown goo standing in the test's bay.
     *
     * @param helper the gametest helper
     * @return the count
     */
    public static int countAround(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(RollingGoo.class, helper.getBounds()).size();
    }
}
