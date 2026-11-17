package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.frost.BurningLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

/**
 * Orb's freeze_blocks puts out the fire blocks and burning mobs in its sphere
 * as it freezes the water and lava there
 * (decision frost-reach-extinguishes-fire-and-burning-mobs), and reads each
 * lava type's cooled block by block id. The freezes themselves reach Blocks,
 * which the unit suite cannot build.
 */
class FreezeBlocksStepTest {

    private static final Vec3 CENTER = new Vec3(0.5, 64.5, 0.5);
    private static final BlockPos FIRE_IN_SPHERE = new BlockPos(0, 65, 1);
    private static final double RADIUS = 2;
    private static final Identifier LAVA = Identifier.parse("minecraft:lava");
    private static final Identifier MOLTEN_IRON = Identifier.parse("goo:molten_iron");
    private static final Identifier OBSIDIAN = Identifier.parse("minecraft:obsidian");
    private static final Identifier IRON_CORE = Identifier.parse("goo:iron_core");
    private static final Identifier THIRD_LIQUID = Identifier.parse("pack:molten_gold");
    private static final Identifier THIRD_COOLED = Identifier.parse("pack:gold_core");
    private static final Identifier UNPAIRED_LIQUID = Identifier.parse("pack:molten_tin");

    @Test
    void theFreezePutsOutTheFireAndTheBurningMobsInItsSphere() {
        Entity burning = BurningLevel.burningAt(1);
        try (BurningLevel world = new BurningLevel(Set.of(FIRE_IN_SPHERE), List.of(burning))) {
            FreezeBlocksStep.freezeWithin(world.level(), CENTER, RADIUS, false);

            world.assertPutOut(FIRE_IN_SPHERE);
            verify(burning).clearFire();
        }
    }

    /**
     * Frost reads what each lava type cools into from the molten groups by position
     * (decision frost-cools-molten-blocks-into-molten-cooled-blocks).
     */
    @Nested
    class CooledForm {

        private final Map<String, List<Identifier>> groups = Map.of(
                "molten_blocks", List.of(LAVA, MOLTEN_IRON, THIRD_LIQUID, UNPAIRED_LIQUID),
                "molten_cooled_blocks", List.of(OBSIDIAN, IRON_CORE, THIRD_COOLED));

        @Test
        void aThirdPairCoolsItsLiquidIntoItsCooledBlock() {
            assertEquals(Optional.of(THIRD_COOLED), FreezeBlocksStep.cooledFormOf(groups, THIRD_LIQUID));
        }

        @Test
        void aMoltenMemberWithNoPartnerCoolsIntoNothing() {
            assertEquals(Optional.empty(), FreezeBlocksStep.cooledFormOf(groups, UNPAIRED_LIQUID));
        }
    }
}
