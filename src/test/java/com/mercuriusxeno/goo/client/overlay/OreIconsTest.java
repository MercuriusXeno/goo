package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.crystal.OreVeins;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A revealed vein's icon shows from the tick the front reaches it, pops in,
 * shrinks away at its life's end, keeps showing when a later ping finds it
 * again, and projects onto the gui through the camera
 * (decision glitter-sphere-icons-gem-ore-groups).
 */
class OreIconsTest {

    private static final Identifier DIAMOND = Identifier.withDefaultNamespace("diamond_ore");
    private static final OreVeins.Vein VEIN = new OreVeins.Vein(DIAMOND, new Vec3(10, 0, 0), 2);
    private static final float TOLERANCE = 1e-4f;

    @AfterEach
    void clear() {
        OreIcons.CLIENT.clear();
    }

    @Test
    void aVeinShowsOnlyOnceTheFrontReachesItAndShrinksAwayAtItsEnd() {
        OreIcons.CLIENT.reveal(0, List.of(VEIN), List.of(10), 100);

        assertTrue(OreIcons.CLIENT.iconsAt(9).isEmpty());
        assertEquals(1f, OreIcons.CLIENT.iconsAt(50).getFirst().scale(), TOLERANCE);
        assertEquals(0.5f, OreIcons.CLIENT.iconsAt(100).getFirst().scale(), TOLERANCE);
        assertTrue(OreIcons.CLIENT.iconsAt(110).isEmpty());
    }

    @Test
    void aVeinFoundAgainKeepsShowingUntilItsNewLifeEnds() {
        OreIcons.CLIENT.reveal(0, List.of(VEIN), List.of(10), 100);
        OreIcons.CLIENT.reveal(32, List.of(VEIN), List.of(10), 100);

        assertEquals(1, OreIcons.CLIENT.iconsAt(40).size());
        assertEquals(1f, OreIcons.CLIENT.iconsAt(40).getFirst().scale(), TOLERANCE);
        assertEquals(1f, OreIcons.CLIENT.iconsAt(115).getFirst().scale(), TOLERANCE);
    }

    @Test
    void closeVeinsOfOneOreShowAsOneIcon() {
        OreIcons.CLIENT.reveal(0, List.of(VEIN, new OreVeins.Vein(DIAMOND, new Vec3(13, 0, 0), 1)),
                List.of(0, 0), 100);

        assertEquals(1, OreIcons.CLIENT.iconsAt(50).size());
    }

    @Test
    void aPointAheadProjectsThroughTheCameraAndOneBehindDoesNot() {
        Matrix4f clip = new Matrix4f().perspective((float) Math.toRadians(90), 1f, 0.05f, 100f);

        assertArrayEquals(new float[]{50f, 50f}, OreIcons.toGui(clip, new Vec3(0, 0, -5), 100, 100).orElseThrow(),
                TOLERANCE);
        assertArrayEquals(new float[]{100f, 50f}, OreIcons.toGui(clip, new Vec3(5, 0, -5), 100, 100).orElseThrow(),
                TOLERANCE);
        assertTrue(OreIcons.toGui(clip, new Vec3(0, 0, 5), 100, 100).isEmpty());
    }
}
