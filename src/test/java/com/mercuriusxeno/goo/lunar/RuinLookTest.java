package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.block.alienstructure.AlienMaterial;
import com.mercuriusxeno.goo.block.alienstructure.PillarStyle;
import com.mercuriusxeno.goo.lunar.RuinLook.LaidBlock;
import com.mercuriusxeno.goo.lunar.WarrenRuinShapes.Part;
import com.mercuriusxeno.goo.lunar.WarrenRuinShapes.Shape;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The warrens' ruins place material families by design, never by roll: within one placement every part takes one
 * material and every pillar one style, and across placements the ruins lay at least three
 * materials, a marker-draft family among them.
 * decision buildings-place-material-families-by-design-never-by-roll
 */
class RuinLookTest {

    private static final int PLACEMENTS = 200;
    private static final int FEWEST_MATERIALS = 3;
    private static final Set<AlienMaterial> MARKER_DRAFT_FAMILIES = EnumSet.complementOf(EnumSet.of(
            AlienMaterial.GLAZED, AlienMaterial.IRIDESCENT));

    @Test
    void eachPlacementFixesItsPartsAndTheRuinsVaryAcrossPlacements() {
        Set<AlienMaterial> laidMaterials = EnumSet.noneOf(AlienMaterial.class);
        for (int seed = 0; seed < PLACEMENTS; seed++) {
            for (Shape shape : WarrenRuinShapes.ALL) {
                RandomSource random = RandomSource.create(seed);
                List<LaidBlock> laid = RuinLook.pick(random).lay(shape);
                assertOnePerPart(shape, seed, laid);
                laid.stream().map(LaidBlock::material).filter(m -> m != null).forEach(laidMaterials::add);
            }
        }
        assertTrue(laidMaterials.size() >= FEWEST_MATERIALS, "the ruins laid only " + laidMaterials);
        assertTrue(laidMaterials.stream().anyMatch(MARKER_DRAFT_FAMILIES::contains),
                "no ruin laid a marker-draft family: " + laidMaterials);
    }

    private static void assertOnePerPart(Shape shape, int seed, List<LaidBlock> laid) {
        Map<Part, Set<AlienMaterial>> materials = new EnumMap<>(Part.class);
        Set<PillarStyle> styles = EnumSet.noneOf(PillarStyle.class);
        for (LaidBlock block : laid) {
            Part member = block.placement().part().member();
            if (member == null) {
                continue;
            }
            materials.computeIfAbsent(member, p -> EnumSet.noneOf(AlienMaterial.class)).add(block.material());
            if (member == Part.PILLAR) {
                styles.add(block.style());
            }
        }
        String where = shape.name() + " at seed " + seed;
        materials.forEach((part, set) -> assertTrue(set.size() == 1, where + " lays " + part + " in " + set));
        assertTrue(styles.size() <= 1, where + " carves its pillars in " + styles);
    }
}
