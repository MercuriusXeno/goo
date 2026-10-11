package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.crystal.OreVeins;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A revealed vein's ore blocks show through walls from the tick the front
 * reaches it, fade in, fade away at its life's end, keep showing when a
 * later hold finds the vein again, and burst once as they begin to show
 * (decision glitter-sphere-icons-gem-ore-groups).
 */
class OreSightingsTest {

    private static final Identifier DIAMOND = Identifier.withDefaultNamespace("diamond_ore");
    private static final List<BlockPos> BLOCKS = List.of(new BlockPos(10, 0, 0), new BlockPos(10, 0, 1));
    private static final OreVeins.Vein VEIN = new OreVeins.Vein(DIAMOND, new Vec3(10.5, 0.5, 1), BLOCKS);
    private static final float TOLERANCE = 1e-4f;

    @AfterEach
    void clear() {
        OreSightings.CLIENT.clear();
    }

    @Test
    void aVeinsBlocksShowOnlyOnceTheFrontReachesItAndFadeAwayAtItsEnd() {
        OreSightings.CLIENT.reveal(10, List.of(VEIN), 100);

        assertTrue(OreSightings.CLIENT.showingAt(9).isEmpty());
        assertEquals(0.5f, OreSightings.CLIENT.showingAt(12).getFirst().shown(), TOLERANCE);
        List<OreSightings.Sighting> settled = OreSightings.CLIENT.showingAt(50);
        assertEquals(BLOCKS, settled.stream().map(OreSightings.Sighting::pos).toList());
        assertEquals(1f, settled.getFirst().shown(), TOLERANCE);
        assertEquals(0.5f, OreSightings.CLIENT.showingAt(100).getFirst().shown(), TOLERANCE);
        assertTrue(OreSightings.CLIENT.showingAt(110).isEmpty());
    }

    @Test
    void aVeinFoundAgainKeepsShowingUntilItsNewLifeEnds() {
        OreSightings.CLIENT.reveal(10, List.of(VEIN), 100);
        OreSightings.CLIENT.reveal(42, List.of(VEIN), 100);

        assertEquals(BLOCKS.size(), OreSightings.CLIENT.showingAt(40).size());
        assertEquals(1f, OreSightings.CLIENT.showingAt(40).getFirst().shown(), TOLERANCE);
        assertEquals(1f, OreSightings.CLIENT.showingAt(115).getFirst().shown(), TOLERANCE);
    }

    @Test
    void aVeinBurstsOnlyInTheTickItBeginsToShowAndNotAgainWhenFoundAgain() {
        OreSightings.CLIENT.reveal(10, List.of(VEIN), 100);

        assertTrue(OreSightings.CLIENT.burstsBetween(8, 9).isEmpty());
        assertEquals(List.of(VEIN.centroid()), OreSightings.CLIENT.burstsBetween(9, 10));
        assertTrue(OreSightings.CLIENT.burstsBetween(10, 11).isEmpty());
        OreSightings.CLIENT.reveal(42, List.of(VEIN), 100);
        assertTrue(OreSightings.CLIENT.burstsBetween(32, 50).isEmpty());
    }
}
