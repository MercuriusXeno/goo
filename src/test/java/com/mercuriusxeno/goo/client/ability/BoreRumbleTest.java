package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.StreamSound;
import com.mercuriusxeno.goo.ability.program.BoreDrillShape;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bore plays two of Build's rumbles: a quiet ambient whir while held, and a
 * louder rumble while it is making progress destroying blocks
 * (decision bore-loops-spires-earth-rumble).
 */
class BoreRumbleTest {

    @Test
    void theProgressRumbleIsLouderThanTheAmbientWhir() {
        StreamSound ambient = AbilityJson.decode("rock_bore").delivery().sound().orElseThrow();
        assertTrue(BoreRumble.PROGRESS_VOLUME > ambient.volume(),
                "the progress rumble at " + BoreRumble.PROGRESS_VOLUME + " is no louder than the whir at "
                        + ambient.volume());
    }

    @Test
    void theProgressRumbleSoundsOnlyWhileBlocksAreBreaking() {
        assertTrue(BoreRumble.rumblesFor(List.of(new BlockPos(1, 64, 0))));
        assertFalse(BoreRumble.rumblesFor(List.of()));
    }

    /**
     * Through Mortar the rumble reads the giga drill's pick, the blocks the server progresses
     * (decisions only-the-blocks-about-to-break-shake-briefly-and-gently,
     * boring-through-rock-goo-gives-a-giga-drill-that-drains-it).
     */
    @Test
    void throughMortarTheBreakingBlocksAreTheGigaDrillsPick() {
        List<BlockPos> giga = BoreStep.progressing(MortarSight.EYE, MortarSight.GIGA, MortarSight.GLOVE_RIGHT,
                MortarSight.AIM, MortarSight.passable(), MortarSight.breakable());
        List<BlockPos> own = BoreStep.progressing(MortarSight.EYE, BoreDrillShape.SHIPPED, MortarSight.GLOVE_RIGHT,
                MortarSight.AIM, MortarSight.passable(), MortarSight.breakable());

        List<BlockPos> breaking = BoreRumble.progressingAlong(MortarSight.EYE, MortarSight.BORE,
                MortarSight.GLOVE_RIGHT, MortarSight.AIM, MortarSight.blocks(true));

        assertEquals(Set.copyOf(giga), Set.copyOf(breaking));
        assertTrue(giga.size() > own.size(), "the giga pick " + giga + " holds no more than the own pick " + own);
    }
}
