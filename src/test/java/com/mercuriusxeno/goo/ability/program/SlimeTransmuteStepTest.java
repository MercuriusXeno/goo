package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the slime transmute's draw categories, monsters for Spawn and the
 * friendly ones for Shape, and that the morph reaches the watchers before
 * the born mob does (decision spawn-hostile-shape-peaceful-from-a-slime).
 */
class SlimeTransmuteStepTest {

    @Test
    void aHostileDrawTakesOnlyMonsters() {
        assertEquals(List.of(MobCategory.MONSTER), drawn(true));
    }

    @Test
    void aPeacefulDrawTakesEveryFriendlyCategory() {
        assertEquals(Arrays.stream(MobCategory.values()).filter(MobCategory::isFriendly).toList(), drawn(false));
    }

    @Test
    void theTransformationGoesOutBeforeTheMobIsAdded() {
        List<String> sent = new ArrayList<>();

        SlimeTransmuteStep.spawnAnnounced("mob", () -> sent.add("transformation"), mob -> sent.add(mob + " spawn"));

        assertEquals(List.of("transformation", "mob spawn"), sent);
    }

    private static List<MobCategory> drawn(boolean hostile) {
        return Arrays.stream(MobCategory.values()).filter(SlimeTransmuteStep.categoryFilter(hostile)).toList();
    }
}
