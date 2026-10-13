package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for void's Erase: a goo of it lands on a mob through
 * {@link GooEffectScheduler#applyEffect}, the path an arrived throw takes.
 * A zombie holding a sword it always drops leaves the world with no death
 * and no item on the floor; a wither stands untouched.
 * void-blob-deletes-the-struck-mob
 */
@RegistersGameTests
public final class VoidEraseTests {
    private static final String VOID_DELETES_THE_STRUCK_MOB = "void_deletes_the_struck_mob";
    private static final String VOID_SPARES_A_BOSS = "void_spares_a_boss";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, VOID_DELETES_THE_STRUCK_MOB, VoidEraseTests::voidDeletesTheStruckMob);
        GooTestFunctions.registerTest(registrar, VOID_SPARES_A_BOSS, VoidEraseTests::voidSparesABoss);
    }

    private static final BlockPos SPAWN_POS = new BlockPos(3, 1, 3);
    /** The tick after spawning, once the level's entity index holds the spawned mob. */
    private static final int SETTLE_TICKS = 1;
    /** Ticks after the strike a dropped item would have landed in the bay. */
    private static final int DROP_SETTLE_TICKS = 5;
    /** Blocks around the mob searched for a dropped item. */
    private static final double DROP_SEARCH_RADIUS = 4;
    private static final float ALWAYS_DROPS = 2.0f;
    private static final Identifier VOID_ERASE = Identifier.parse("goo:void_erase");
    private static final String ABILITY_REQUIRED = "Ability registry must hold void_erase";
    private static final String SHOULD_LEAVE = "The struck zombie should be gone from the world, removed %s, alive %s";
    private static final String SHOULD_NOT_DIE = "The struck zombie should leave by discard, not death: %s";
    private static final String SHOULD_DROP_NOTHING = "Erase should leave no item where the zombie stood: %s";
    private static final String SHOULD_SPARE_BOSS = "A wither should stand in the world after Erase";

    private VoidEraseTests() {
    }

    /**
     * A zombie holding a sword it always drops, struck by Erase, is gone from
     * the world, removed by discard rather than killed, and no item lies
     * where it stood.
     *
     * @param helper the gametest helper
     */
    public static void voidDeletesTheStruckMob(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setDropChance(EquipmentSlot.MAINHAND, ALWAYS_DROPS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            AABB around = zombie.getBoundingBox().inflate(DROP_SEARCH_RADIUS);
            strike(helper, zombie);
            helper.assertTrue(zombie.isRemoved() && !zombie.isAlive(),
                    String.format(SHOULD_LEAVE, zombie.getRemovalReason(), zombie.isAlive()));
            helper.assertTrue(zombie.getRemovalReason() == Entity.RemovalReason.DISCARDED,
                    String.format(SHOULD_NOT_DIE, zombie.getRemovalReason()));
            helper.runAfterDelay(DROP_SETTLE_TICKS, () -> {
                var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
                helper.assertTrue(dropped.isEmpty(), String.format(SHOULD_DROP_NOTHING, dropped));
                helper.succeed();
            });
        });
    }

    /**
     * A wither struck by Erase stands in the world.
     *
     * @param helper the gametest helper
     */
    public static void voidSparesABoss(GameTestHelper helper) {
        Mob wither = helper.spawnWithNoFreeWill(EntityType.WITHER, SPAWN_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, wither);
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(wither.isAlive() && !wither.isRemoved(), SHOULD_SPARE_BOSS);
                wither.discard();
                helper.succeed();
            });
        });
    }

    private static void strike(GameTestHelper helper, Mob mob) {
        AbilityDefinition erase = AbilityRegistry.of(helper.getLevel()).getAbility(VOID_ERASE);
        helper.assertTrue(erase != null, ABILITY_REQUIRED);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, erase.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, VOID_ERASE.toString()));
    }
}
