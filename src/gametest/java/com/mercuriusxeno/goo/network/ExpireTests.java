package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Gametests for yore Expire: a goo of it lands on a mob through
 * {@link GooEffectScheduler#applyEffect}, the path an arrived throw takes.
 * A zombie in netherite under Resistance V is dead the next tick; a wither
 * is untouched.
 * expire-kills-the-struck-mob
 */
public final class ExpireTests {

    private static final BlockPos SPAWN_POS = new BlockPos(3, 1, 3);
    /** The tick after spawning, once the level's entity index holds the spawned mob. */
    private static final int SETTLE_TICKS = 1;
    /** Resistance V, the amplifier that cancels every ordinary hit. */
    private static final int FULL_RESISTANCE = 4;
    private static final int EFFECT_TICKS = 200;
    private static final Identifier YORE_EXPIRE = Identifier.parse("goo:yore_expire");
    private static final String ABILITY_REQUIRED = "Ability registry must hold yore_expire";
    private static final String SHOULD_DIE = "The armored, resistant zombie should be dead the tick after Expire";
    private static final String SHOULD_SPARE_BOSS = "A wither should keep its health under Expire";

    private ExpireTests() {
    }

    /**
     * A zombie in full netherite under Resistance V, struck by Expire, is
     * dead the next tick.
     *
     * @param helper the gametest helper
     */
    public static void expireKillsTheStruckZombie(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        zombie.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, EFFECT_TICKS, FULL_RESISTANCE));
        helper.runAfterDelay(SETTLE_TICKS, () -> strike(helper, zombie));
        helper.runAfterDelay(SETTLE_TICKS + 1, () -> {
            helper.assertTrue(zombie.isDeadOrDying(), SHOULD_DIE);
            helper.succeed();
        });
    }

    /**
     * A wither struck by Expire keeps its health.
     *
     * @param helper the gametest helper
     */
    public static void expireSparesABoss(GameTestHelper helper) {
        Mob wither = helper.spawnWithNoFreeWill(EntityType.WITHER, SPAWN_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            float before = wither.getHealth();
            strike(helper, wither);
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(wither.isAlive() && wither.getHealth() >= before, SHOULD_SPARE_BOSS);
                wither.discard();
                helper.succeed();
            });
        });
    }

    private static void strike(GameTestHelper helper, Mob mob) {
        AbilityDefinition expire = AbilityRegistry.of(helper.getLevel()).getAbility(YORE_EXPIRE);
        helper.assertTrue(expire != null, ABILITY_REQUIRED);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, expire.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, YORE_EXPIRE.toString()));
    }
}
