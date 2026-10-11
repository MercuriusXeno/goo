package com.mercuriusxeno.goo.ability.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Glitter groups touching blocks of one ore into a vein at their centroid,
 * and merges veins of one ore lying close together into one icon
 * (decision glitter-sphere-icons-gem-ore-groups).
 */
class OreVeinsTest {

    private static final Identifier DIAMOND = Identifier.withDefaultNamespace("diamond_ore");
    private static final Identifier LAPIS = Identifier.withDefaultNamespace("lapis_ore");
    private static final double TOLERANCE = 1e-9;

    @Test
    void blocksTouchingAtAFaceOrACornerFormOneVeinAtTheirCentroid() {
        List<OreVeins.Vein> veins = OreVeins.group(Map.of(
                new BlockPos(0, 0, 0), DIAMOND, new BlockPos(1, 0, 0), DIAMOND, new BlockPos(2, 1, 1), DIAMOND));

        assertEquals(1, veins.size());
        assertEquals(3, veins.getFirst().count());
        assertTrue(veins.getFirst().blocks().contains(new BlockPos(2, 1, 1)));
        Vec3 centroid = veins.getFirst().centroid();
        assertEquals(1.5, centroid.x, TOLERANCE);
        assertEquals(5.0 / 6, centroid.y, TOLERANCE);
    }

    @Test
    void blocksApartOrOfAnotherOreFormVeinsOfTheirOwn() {
        List<OreVeins.Vein> veins = OreVeins.group(Map.of(
                new BlockPos(0, 0, 0), DIAMOND, new BlockPos(1, 0, 0), LAPIS, new BlockPos(5, 0, 0), DIAMOND));

        assertEquals(3, veins.size());
    }
}
