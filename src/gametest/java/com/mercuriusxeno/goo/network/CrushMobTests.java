package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametest for rock crush striking a mob directly: the blob's program runs on
 * the struck mob, which takes its force damage, and no crater is blasted, so
 * mob attack never mixes with block crush
 * (decision crush-blob-breaks-along-its-strike).
 */
public final class CrushMobTests {

    private static final Identifier CRUSH = Identifier.fromNamespaceAndPath(Goo.MODID, "rock_crush");
    /** The stone the zombie stands on. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_crush";
    private static final String STRIKE_UNHURT = "The zombie the blob struck took no damage";

    private CrushMobTests() {
    }

    /**
     * Crush strikes a zombie standing on stone directly: the zombie is hurt
     * and the stone under it stands.
     *
     * @param helper the gametest helper
     */
    public static void crushStrikesAMob(GameTestHelper helper) {
        AbilityDefinition crush = AbilityRegistry.of(helper.getLevel()).getAbility(CRUSH);
        helper.assertTrue(crush != null, ABILITY_REQUIRED);
        helper.setBlock(FLOOR, Blocks.STONE);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FLOOR.above());
        helper.runAfterDelay(1, () -> {
            GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, crush.gooType(),
                    zombie.getId(), zombie.blockPosition(), Direction.UP, CRUSH.toString()));
            helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), STRIKE_UNHURT);
            helper.assertBlockPresent(Blocks.STONE, FLOOR);
            helper.succeed();
        });
    }
}
