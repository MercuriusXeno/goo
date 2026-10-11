package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametest for Quantum's shared fate: a player's link struck on two cows in
 * turn pairs them, so hurting one lowers the other's health, and a potion
 * effect on one lands on both (decision shared-fate-pools-health-and-status).
 */
@RegistersGameTests
public final class SharedFateTests {
    private static final String SHARED_FATE_MIRRORS = "shared_fate_mirrors_health_and_effects";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SHARED_FATE_MIRRORS, SharedFateTests::sharedFateMirrorsHealthAndEffects);
    }


    private static final String LINK = "goo:quantum_link";
    private static final BlockPos FIRST_COW = new BlockPos(2, 1, 2);
    private static final BlockPos SECOND_COW = new BlockPos(4, 1, 4);
    private static final float HIT = 4.0F;
    private static final int SLOWNESS_TICKS = 200;
    private static final int SETTLE_TICKS = 1;
    private static final int MIRROR_TICKS = 3;

    private SharedFateTests() {
    }

    /**
     * Two cows linked by one player's casts mirror a hit on either and a
     * potion effect on either.
     *
     * @param helper the gametest helper
     */
    public static void sharedFateMirrorsHealthAndEffects(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        Mob first = helper.spawnWithNoFreeWill(EntityType.COW, FIRST_COW);
        Mob second = helper.spawnWithNoFreeWill(EntityType.COW, SECOND_COW);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, first, caster);
            strike(helper, second, caster);
            helper.assertTrue(first.getData(GooAttachments.SHARED_FATE).standsAt(helper.getLevel().getGameTime())
                    && second.getData(GooAttachments.SHARED_FATE).standsAt(helper.getLevel().getGameTime()),
                    "The second strike should link the two cows");
            first.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), HIT);
        });
        helper.runAfterDelay(SETTLE_TICKS + MIRROR_TICKS, () -> {
            helper.assertTrue(second.getHealth() < second.getMaxHealth(),
                    "Hurting one linked cow should lower the other's health, which stands at " + second.getHealth());
            helper.assertTrue(first.getHealth() == second.getHealth(),
                    "Both cows should stand at the same health, " + first.getHealth() + " and " + second.getHealth());
            first.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_TICKS));
            helper.assertTrue(second.hasEffect(MobEffects.SLOWNESS), "A potion on one linked cow should land on both");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    private static void strike(GameTestHelper helper, Mob mob, ServerPlayer thrower) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(LINK));
        helper.assertTrue(ability != null, "The link ability should load");
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), thrower, ability.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, LINK));
    }
}
