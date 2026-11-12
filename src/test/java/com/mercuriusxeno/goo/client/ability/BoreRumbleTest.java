package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.StreamSound;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bore plays two of Spire's rumbles: a quiet ambient whir while held, and a
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
}
