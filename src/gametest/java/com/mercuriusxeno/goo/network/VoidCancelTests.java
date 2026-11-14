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
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for void's Cancel: a goo of it lands on a mob through
 * {@link GooEffectScheduler#applyEffect}, the path an arrived throw takes.
 * A zombie holding a sword it always drops leaves the world with no death
 * and no item on the floor; a wither stands untouched.
 * void-blob-deletes-the-struck-mob
 */
@RegistersGameTests
public final class VoidCancelTests {
    private static final String VOID_DELETES_THE_STRUCK_MOB = "void_deletes_the_struck_mob";
    private static final String VOID_SPARES_A_BOSS = "void_spares_a_boss";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, VOID_DELETES_THE_STRUCK_MOB, VoidCancelTests::voidDeletesTheStruckMob);
        GooTestFunctions.registerTest(registrar, VOID_SPARES_A_BOSS, VoidCancelTests::voidSparesABoss);
    }

    private static final BlockPos SPAWN_POS = new BlockPos(3, 1, 3);
    /** The tick after spawning, once the level's entity index holds the spawned mob. */
    private static final int SETTLE_TICKS = 1;
    private static final Identifier VOID_CANCEL = Identifier.parse("goo:void_cancel");
    private static final String ABILITY_REQUIRED = "Ability registry must hold void_cancel";
    private static final String SHOULD_LEAVE = "The struck zombie should be gone from the world, removed %s, alive %s";
    private static final String SHOULD_NOT_DIE = "The struck zombie should leave by discard, not death: %s";
    private static final String SHOULD_SPARE_BOSS = "A wither should stand in the world after Cancel";

    private VoidCancelTests() {
    }

    /**
     * A zombie struck by Cancel is gone from the world, removed by discard
     * rather than killed, so it runs no death and drops nothing.
     *
     * @param helper the gametest helper
     */
    public static void voidDeletesTheStruckMob(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, zombie);
            helper.assertTrue(zombie.isRemoved() && !zombie.isAlive(),
                    String.format(SHOULD_LEAVE, zombie.getRemovalReason(), zombie.isAlive()));
            helper.assertTrue(zombie.getRemovalReason() == Entity.RemovalReason.DISCARDED,
                    String.format(SHOULD_NOT_DIE, zombie.getRemovalReason()));
            helper.succeed();
        });
    }

    /**
     * A wither struck by Cancel stands in the world.
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
        AbilityDefinition cancel = AbilityRegistry.of(helper.getLevel()).getAbility(VOID_CANCEL);
        helper.assertTrue(cancel != null, ABILITY_REQUIRED);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, cancel.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, VOID_CANCEL.toString()));
    }
}
