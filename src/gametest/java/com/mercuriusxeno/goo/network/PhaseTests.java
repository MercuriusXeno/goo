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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Phase keeps the phased on their own plane: a zombie's hit on a phased
 * player lands nothing and the zombie cannot take the player as its target,
 * while a phased player's hit on a zombie the phase shift struck lands.
 * phase-shares-a-plane-between-the-phased
 */
public final class PhaseTests {

    private static final Identifier PHASE = Identifier.parse("goo:quantum_phase");
    private static final String PHASE_SHIFT = "goo:quantum_phase_shift";
    private static final BlockPos ZOMBIE_POS = new BlockPos(3, 1, 3);
    private static final float HIT = 4.0F;
    private static final int SETTLE_TICKS = 1;

    private PhaseTests() {
    }

    /**
     * A player holding Phase keeps full health under a zombie's hit, and the
     * zombie's aim at the player is turned away.
     *
     * @param helper the gametest helper
     */
    public static void phasedPlayerIgnoredByZombie(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.QUANTUM, PHASE);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(player.getData(GooAttachments.OUT_OF_PHASE).standsAt(helper.getLevel().getGameTime()),
                    "Phase should put the player out of phase");
            float health = player.getHealth();
            HeartOverlayTests.hurt(helper, player, helper.getLevel().damageSources().mobAttack(zombie), HIT);
            helper.assertTrue(player.getHealth() == health,
                    "A zombie's hit should land nothing on a phased player, health " + player.getHealth());
            zombie.setTarget(player);
            helper.assertTrue(zombie.getTarget() == null, "A zombie should not take a phased player as its target");
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * A phased player's hit lands on a zombie the phase shift struck.
     *
     * @param helper the gametest helper
     */
    public static void phasedPairHurtEachOther(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.QUANTUM, PHASE);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, zombie, player);
            helper.assertTrue(zombie.getData(GooAttachments.OUT_OF_PHASE).standsAt(helper.getLevel().getGameTime()),
                    "The phase shift should put the zombie out of phase");
            float zombieHealth = zombie.getHealth();
            zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
            helper.assertTrue(zombie.getHealth() < zombieHealth,
                    "A phased player's hit should land on a phased zombie, health " + zombie.getHealth());
            float playerHealth = player.getHealth();
            HeartOverlayTests.hurt(helper, player, helper.getLevel().damageSources().mobAttack(zombie), HIT);
            helper.assertTrue(player.getHealth() < playerHealth,
                    "A phased zombie's hit should land on a phased player, health " + player.getHealth());
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static void strike(GameTestHelper helper, Mob mob, ServerPlayer thrower) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(PHASE_SHIFT));
        helper.assertTrue(ability != null, "The phase shift ability should load");
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), thrower, ability.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, PHASE_SHIFT));
    }
}
