package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametest for rock crush: a blob striking the west face of a dirt bank
 * breaks the bank three blocks deep along the strike, leaves the bank past
 * that depth and above the strike standing, and hurts the zombie standing at
 * the landing and shoves it along the strike
 * (decision crush-blob-breaks-along-its-strike).
 */
public final class CrushStrikeTests {

    private static final Identifier CRUSH = Identifier.fromNamespaceAndPath(Goo.MODID, "rock_crush");
    /** The bank's first block, struck on its west face. */
    private static final BlockPos STRUCK = new BlockPos(2, 1, 2);
    /** rock_crush.json's depth. */
    private static final int DEPTH = 3;
    /** The cell in front of the struck face, where the blob lands and the zombie stands. */
    private static final BlockPos LANDING = STRUCK.west();
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_crush";
    private static final String ZOMBIE_UNHURT = "The zombie at the landing took no damage";
    private static final String ZOMBIE_NOT_SHOVED = "The zombie at the landing moved %s, not east along the strike";

    private CrushStrikeTests() {
    }

    /**
     * Crush lands on the bank's west face: the three blocks east of the
     * landing are cut, the fourth and the block above the struck one stand,
     * and the zombie at the landing is hurt and moving east.
     *
     * @param helper the gametest helper
     */
    public static void crushBreaksAlongTheStrike(GameTestHelper helper) {
        AbilityDefinition crush = AbilityRegistry.of(helper.getLevel()).getAbility(CRUSH);
        helper.assertTrue(crush != null, ABILITY_REQUIRED);
        for (int deep = 0; deep <= DEPTH; deep++) {
            helper.setBlock(STRUCK.east(deep), Blocks.DIRT);
            helper.setBlock(STRUCK.east(deep).above(), Blocks.DIRT);
        }
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, LANDING);
        Vec3 aim = Vec3.atCenterOf(helper.absolutePos(STRUCK)).add(-0.5, 0, 0);
        helper.runAfterDelay(1, () -> {
            AbilityImpact.land(helper.getLevel(), helper.absolutePos(STRUCK), GooTypes.ROCK, Direction.WEST, crush,
                    aim);
            Vec3 motion = zombie.getDeltaMovement();
            helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), ZOMBIE_UNHURT);
            helper.assertTrue(motion.x > 0, String.format(ZOMBIE_NOT_SHOVED, motion));
            for (int deep = 0; deep < DEPTH; deep++) {
                helper.assertBlockPresent(Blocks.AIR, STRUCK.east(deep));
            }
            helper.assertBlockPresent(Blocks.DIRT, STRUCK.east(DEPTH));
            helper.assertBlockPresent(Blocks.DIRT, STRUCK.above());
            helper.succeed();
        });
    }
}
