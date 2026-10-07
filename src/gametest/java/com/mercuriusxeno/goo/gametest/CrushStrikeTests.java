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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametest for rock crush: a blob landing on the top of a dirt bed blasts a
 * radius 2 crater, every block within it breaking and dropping, the blocks
 * past it standing; the zombie it lands on is hurt and the zombie at the
 * crater's edge is not (decision crush-blob-breaks-along-its-strike).
 */
public final class CrushStrikeTests {

    private static final Identifier CRUSH = Identifier.fromNamespaceAndPath(Goo.MODID, "rock_crush");
    /** The block the blob strikes, on top of the dirt bed. */
    private static final BlockPos STRUCK = new BlockPos(2, 1, 2);
    /** Two blocks east of the struck one, its center just past the crater's radius. */
    private static final BlockPos PAST_THE_RIM = STRUCK.east(2);
    private static final double ITEM_SEARCH_RADIUS = 3;
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_crush";
    private static final String STRUCK_UNHURT = "The zombie the blob landed on took no damage";
    private static final String EDGE_HURT = "The zombie at the crater's edge was hurt, which only the struck mob is";

    private CrushStrikeTests() {
    }

    /**
     * Crush lands on the dirt bed's top: the struck block, the one under it
     * and the one beside it break, the blocks two away stand, dirt drops,
     * and only the zombie standing on the struck block is hurt.
     *
     * @param helper the gametest helper
     */
    public static void crushBlastsACrater(GameTestHelper helper) {
        AbilityDefinition crush = AbilityRegistry.of(helper.getLevel()).getAbility(CRUSH);
        helper.assertTrue(crush != null, ABILITY_REQUIRED);
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
            }
        }
        Mob struck = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, STRUCK.above());
        Mob atTheEdge = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, PAST_THE_RIM.above());
        Vec3 aim = Vec3.atCenterOf(helper.absolutePos(STRUCK)).add(0, 0.5, 0);
        helper.runAfterDelay(1, () -> {
            AbilityImpact.land(helper.getLevel(), helper.absolutePos(STRUCK), GooTypes.ROCK, Direction.UP, crush, aim);
            helper.assertBlockPresent(Blocks.AIR, STRUCK);
            helper.assertBlockPresent(Blocks.AIR, STRUCK.below());
            helper.assertBlockPresent(Blocks.AIR, STRUCK.east());
            helper.assertBlockPresent(Blocks.DIRT, PAST_THE_RIM);
            helper.assertBlockPresent(Blocks.DIRT, STRUCK.south(2));
            helper.assertItemEntityPresent(Items.DIRT, STRUCK, ITEM_SEARCH_RADIUS);
            helper.assertTrue(struck.getHealth() < struck.getMaxHealth(), STRUCK_UNHURT);
            helper.assertTrue(atTheEdge.getHealth() == atTheEdge.getMaxHealth(), EDGE_HURT);
            helper.succeed();
        });
    }
}
