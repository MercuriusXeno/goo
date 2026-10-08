package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.root.RootEvents;
import com.mercuriusxeno.goo.ability.root.Rooted;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for Leaf Vines: a blob striking a mob roots it, holding it
 * within the leash, thorning it for moving and dragging it back onto the root, multiplying the
 * fire it takes and breaking after its hits; a blob missing every mob
 * lingers on the ground as a trap rooting the first mob to walk in. They
 * sit in the scheduler's package because the scheduler is package-private.
 * vines-unpack-root-and-thorn
 */
public final class VinesTests {

    private static final String ABILITY_VINES = "goo:leaf_vines";
    private static final String ABILITIES_REQUIRED = "Ability registry must hold leaf_vines";
    /** Clear of the barrier shell the framework wraps the structure in. */
    private static final BlockPos SPAWN_POS = new BlockPos(3, 1, 3);
    /** The floor the bay's mobs stand on and the trap lands on. */
    private static final int FLOOR_SPAN = 6;
    private static final int SETTLE_TICKS = 1;
    /** Ticks the zombie is shoved east, inside leaf_vines.json's hold of 300. */
    private static final int SHOVE_TICKS = 50;
    /** A shove far past the leash in one tick. */
    private static final Vec3 SHOVE = new Vec3(0.6, 0, 0);
    /** Ticks after the last shove by which the vines have dragged the zombie onto its root, inside the hold. */
    private static final int DRAG_BACK_TICKS = 8;
    /** Slack on the leash for the double arithmetic of the pull. */
    private static final double LEASH_SLACK = 1e-6;
    /** leaf_vines.json's hits a throw takes before breaking, and its fire factor. */
    private static final int THROW_HITS = 4;
    private static final float FIRE_FACTOR = 1.5f;
    private static final float FIRE_DAMAGE = 2f;
    private static final float HIT_DAMAGE = 1f;
    private static final float HEALTH_TOLERANCE = 1e-3f;
    /** Ticks a mob walking in has to be rooted by, past the trap's first tick. */
    private static final int TRAP_WAIT_TICKS = 3;

    private static final String SHOULD_STAY_LEASHED = "The rooted zombie stood %.3f blocks from its root at tick %d";
    private static final String SHOULD_BE_DRAGGED_BACK = "The vines should drag the zombie onto its root, it stood %.3f off";
    private static final String SHOULD_BE_THORNED = "The zombie shoved past its leash should have taken thorn damage";
    private static final String THORNS_SPEND_NO_HIT = "Thorn damage should spend none of the vines' hits, read %d";
    private static final String SHOULD_BURN_MORE = "Fire of %.1f should take %.2f from the rooted cow, took %.2f";
    private static final String SHOULD_HOLD_PAST_THREE_HITS = "The vines should still hold after three hits";
    private static final String SHOULD_BREAK = "The vines should release the cow at their fourth hit";
    private static final String SHOULD_FALL_AWAY = "The released vines should be gone once they have fallen away";
    private static final String TRAP_SHOULD_STAND = "A blob missing every mob should stand its trap on the ground";
    private static final String TRAP_SHOULD_WAIT = "The trap should root nothing before a mob walks in";
    private static final String SHOULD_ROOT_ENTRANT = "The trap should root the zombie that walked in";
    private static final String TRAP_SHOULD_GO = "The trap should be gone once it rooted the zombie";

    private VinesTests() {
    }

    /**
     * A struck zombie shoved east every tick stays within the leash of where
     * the vines latched it, takes thorn damage for straining, and the thorns
     * spend none of the vines' hits.
     *
     * @param helper the gametest helper
     */
    public static void vinesRootAndThorn(GameTestHelper helper) {
        layFloor(helper);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS);
        // A helmet keeps the daylight from burning it, and the burn from spending the vines' hits.
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float[] before = new float[1];
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, zombie);
            before[0] = zombie.getHealth();
        });
        // Every callback is scheduled here, at the top: one scheduled from inside another runs again.
        for (int tick = 1; tick <= SHOVE_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(SETTLE_TICKS + tick, () -> {
                assertLeashed(helper, zombie, at);
                zombie.setDeltaMovement(SHOVE);
                zombie.hurtMarked = true;
            });
        }
        helper.runAfterDelay(SETTLE_TICKS + SHOVE_TICKS + 1, () -> {
            assertLeashed(helper, zombie, SHOVE_TICKS + 1);
            helper.assertTrue(zombie.getHealth() < before[0], SHOULD_BE_THORNED);
            int hitsLeft = zombie.getData(GooAttachments.ROOTED).hitsLeft();
            helper.assertTrue(hitsLeft == THROW_HITS, String.format(THORNS_SPEND_NO_HIT, hitsLeft));
        });
        helper.runAfterDelay(SETTLE_TICKS + SHOVE_TICKS + DRAG_BACK_TICKS, () -> {
            double across = acrossFromRoot(zombie);
            helper.assertTrue(across <= RootEvents.SLACK_BLOCKS, String.format(SHOULD_BE_DRAGGED_BACK, across));
            helper.succeed();
        });
    }

    /**
     * A struck cow takes fire damage multiplied by the vines' fire factor,
     * the vines hold through three hits and release it at the fourth, and
     * are gone once they have fallen away.
     *
     * @param helper the gametest helper
     */
    public static void vinesBurnAndBreak(GameTestHelper helper) {
        layFloor(helper);
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, cow);
            float before = cow.getHealth();
            hurt(cow, helper.getLevel().damageSources().inFire(), FIRE_DAMAGE);
            float burned = before - cow.getHealth();
            float expected = FIRE_DAMAGE * FIRE_FACTOR;
            helper.assertTrue(Math.abs(burned - expected) < HEALTH_TOLERANCE,
                    String.format(SHOULD_BURN_MORE, FIRE_DAMAGE, expected, burned));
            hurt(cow, helper.getLevel().damageSources().magic(), HIT_DAMAGE);
            hurt(cow, helper.getLevel().damageSources().magic(), HIT_DAMAGE);
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(cow.getData(GooAttachments.ROOTED).holds(now), SHOULD_HOLD_PAST_THREE_HITS);
            hurt(cow, helper.getLevel().damageSources().magic(), HIT_DAMAGE);
            helper.assertFalse(cow.getData(GooAttachments.ROOTED).holds(now), SHOULD_BREAK);
            helper.runAfterDelay(Rooted.FADE_TICKS + 1, () -> {
                helper.assertFalse(cow.hasData(GooAttachments.ROOTED), SHOULD_FALL_AWAY);
                helper.succeed();
            });
        });
    }

    /**
     * A blob landing on the floor stands its trap there, roots nothing while
     * no mob stands in it, and roots the zombie that walks in, the trap going
     * as it does.
     *
     * @param helper the gametest helper
     */
    public static void vinesTrapOnTheGround(GameTestHelper helper) {
        layFloor(helper);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS.east(2));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            AbilityDefinition vines = vines(helper);
            AbilityImpact.land(helper.getLevel(), helper.absolutePos(SPAWN_POS.below()), vines.gooType(),
                    Direction.UP, vines);
            helper.runAfterDelay(SETTLE_TICKS, () -> {
                helper.assertTrue(helper.getBlockState(SPAWN_POS).is(GooBlocks.ABILITY_BLOCK.get()), TRAP_SHOULD_STAND);
                helper.assertFalse(zombie.hasData(GooAttachments.ROOTED), TRAP_SHOULD_WAIT);
                Vec3 cell = Vec3.atBottomCenterOf(helper.absolutePos(SPAWN_POS));
                zombie.setPos(cell);
                helper.runAfterDelay(TRAP_WAIT_TICKS, () -> {
                    long now = helper.getLevel().getGameTime();
                    helper.assertTrue(zombie.getData(GooAttachments.ROOTED).holds(now), SHOULD_ROOT_ENTRANT);
                    helper.assertFalse(helper.getBlockState(SPAWN_POS).is(GooBlocks.ABILITY_BLOCK.get()), TRAP_SHOULD_GO);
                    helper.succeed();
                });
            });
        });
    }

    /**
     * Lays a stone floor under the bay's mobs.
     *
     * @param helper the gametest helper
     */
    private static void layFloor(GameTestHelper helper) {
        for (int x = 1; x <= FLOOR_SPAN; x++) {
            for (int z = 1; z <= FLOOR_SPAN; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static AbilityDefinition vines(GameTestHelper helper) {
        AbilityDefinition vines = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ABILITY_VINES));
        helper.assertTrue(vines != null, ABILITIES_REQUIRED);
        return vines;
    }

    /**
     * Lands one Vines blob on the mob through the scheduler's impact, the
     * path an arrived throw takes.
     *
     * @param helper the gametest helper
     * @param mob    the struck mob
     */
    private static void strike(GameTestHelper helper, Mob mob) {
        AbilityDefinition vines = vines(helper);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, vines.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, ABILITY_VINES));
    }

    /**
     * Hurts a mob past the hurt cooldown a hit before it leaves, so each
     * hit lands whole.
     *
     * @param mob    the mob
     * @param source the damage source
     * @param amount the damage
     */
    private static void hurt(LivingEntity mob, DamageSource source, float amount) {
        mob.invulnerableTime = 0;
        mob.hurtServer((ServerLevel) mob.level(), source, amount);
    }

    private static double acrossFromRoot(LivingEntity mob) {
        Vec3 root = mob.getData(GooAttachments.ROOTED).anchor();
        return Math.hypot(mob.getX() - root.x, mob.getZ() - root.z);
    }

    private static void assertLeashed(GameTestHelper helper, LivingEntity mob, int tick) {
        double across = acrossFromRoot(mob);
        helper.assertTrue(across <= RootEvents.LEASH_BLOCKS + LEASH_SLACK,
                String.format(SHOULD_STAY_LEASHED, across, tick));
    }
}
