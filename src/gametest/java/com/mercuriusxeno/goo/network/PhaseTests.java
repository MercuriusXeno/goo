package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.quantum.OutOfPhase;
import com.mercuriusxeno.goo.ability.quantum.PhaseEvents;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.gametest.SurvivalPlayers;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * The phased keep their own plane: a zombie's hit on a phased player lands
 * nothing and the zombie cannot take the player as its target, a phased
 * player's hit on a phased zombie lands and the zombie's on the player, and a
 * phased zombie passes through a plain one. Each test lays the phase Phase
 * goo lays straight onto its entities, since what the phase means is under
 * test here and PhaseGooTests proves the goo lays it.
 * phase-shares-a-plane-between-the-phased
 */
@RegistersGameTests
public final class PhaseTests {

    private static final String PHASED_PLAYER_IGNORED = "phased_player_ignored_by_zombie";
    private static final String PHASED_PAIR_HURT = "phased_pair_hurt_each_other";
    private static final String PHASED_MOB_PASSES_THROUGH = "phased_mob_passes_through_an_unphased_one";
    private static final String PHASED_ZOMBIE_HUNTS = "phased_zombie_hunts_a_phased_player";
    /** How far apart, across, two overlapping zombies start, so a push between them has a side to go. */
    private static final double OVERLAP_OFFSET = 0.1;
    /** Ticks two overlapping zombies are given to push each other apart. */
    private static final int PUSH_TICKS = 20;
    /** How far a zombie may drift and still count as unpushed. */
    private static final double UNPUSHED = 0.02;
    private static final BlockPos ZOMBIE_POS = new BlockPos(3, 1, 3);
    private static final float HIT = 4.0F;
    private static final int SETTLE_TICKS = 1;
    /**
     * The seed the hunting mob's random is set to as it spawns, so its target
     * goal's roll lands on its first tick, which runs the target goals
     * whatever its id, rather than once a roll lands.
     * gametests-waiting-on-a-random-draw-are-found-and-made-deterministic
     * operator ruling 2026-11-19: seed the drawing random
     */
    private static final long HUNT_SEED = 3L;
    /** The tick by which the hunting mob has run its first tick, which runs its target goals whatever its id. */
    private static final int HUNTED_BY = 1;

    private PhaseTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PHASED_PLAYER_IGNORED, PhaseTests::phasedPlayerIgnoredByZombie);
        GooTestFunctions.registerTest(registrar, PHASED_PAIR_HURT, PhaseTests::phasedPairHurtEachOther);
        GooTestFunctions.registerTest(registrar, PHASED_MOB_PASSES_THROUGH, PhaseTests::phasedMobPassesThrough);
        GooTestFunctions.registerTest(registrar, PHASED_ZOMBIE_HUNTS, PhaseTests::phasedZombieHuntsAPhasedPlayer);
    }

    /**
     * A phased zombie and a plain zombie standing in the same spot do not
     * push each other apart: bodies on opposite sides of phase pass through
     * each other.
     *
     * @param helper the gametest helper
     */
    public static void phasedMobPassesThrough(GameTestHelper helper) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.setBlock(ZOMBIE_POS.below().offset(x, 0, z), Blocks.STONE);
            }
        }
        Mob phased = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, ZOMBIE_POS);
        putOutOfPhase(phased);
        Mob plain = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, ZOMBIE_POS);
        plain.setPos(phased.getX() + OVERLAP_OFFSET, phased.getY(), phased.getZ());
        Vec3 phasedStart = phased.position();
        Vec3 plainStart = plain.position();
        helper.runAfterDelay(PUSH_TICKS, () -> {
            helper.assertTrue(phased.position().distanceTo(phasedStart) < UNPUSHED,
                    "A phased zombie should pass through a plain one, moved to " + phased.position());
            helper.assertTrue(plain.position().distanceTo(plainStart) < UNPUSHED,
                    "A plain zombie should pass through a phased one, moved to " + plain.position());
            helper.succeed();
        });
    }

    /**
     * A phased player keeps full health under a zombie's hit, and the
     * zombie's aim at the player is turned away.
     *
     * @param helper the gametest helper
     */
    public static void phasedPlayerIgnoredByZombie(GameTestHelper helper) {
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        putOutOfPhase(player);
        Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, ZOMBIE_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
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
     * A phased player's hit lands on a phased zombie, and the zombie's on the player.
     *
     * @param helper the gametest helper
     */
    public static void phasedPairHurtEachOther(GameTestHelper helper) {
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        putOutOfPhase(player);
        Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, ZOMBIE_POS);
        putOutOfPhase(zombie);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
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

    /**
     * A free-willed zombie and a player both out of phase share a plane, so
     * the zombie's own goals take the player as its target, its random
     * seeded as it spawns.
     *
     * @param helper the gametest helper
     */
    public static void phasedZombieHuntsAPhasedPlayer(GameTestHelper helper) {
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        putOutOfPhase(player);
        Mob zombie = helper.spawn(EntityTypes.ZOMBIE, ZOMBIE_POS);
        putOutOfPhase(zombie);
        zombie.getRandom().setSeed(HUNT_SEED);
        helper.runAtTickTime(HUNTED_BY, () -> {
            helper.assertTrue(zombie.getTarget() == player,
                    "A phased zombie should target a phased player, targets " + zombie.getTarget()
                            + ", zombie phased " + PhaseEvents.phased(zombie) + ", player phased "
                            + PhaseEvents.phased(player));
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static void putOutOfPhase(Entity entity) {
        entity.setData(GooAttachments.OUT_OF_PHASE, OutOfPhase.held());
    }
}
