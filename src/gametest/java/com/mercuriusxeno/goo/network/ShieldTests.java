package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.ShieldStep;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.gametest.SurvivalPlayers;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Durable's Shield through the real paths, the held channel ticked as the
 * client's hold ticks it: raised, it blocks a mob's hit from the front and
 * not from behind, a left click with it raised bashes the mob in front and
 * none without it, and it spends durable goo for what it blocks and each
 * bash and none for the time it stands raised.
 * durable-goo-forms-a-shield-at-will-and-shield-bashes
 */
@RegistersGameTests
public final class ShieldTests {

    private static final String BLOCKS_FROM_THE_FRONT = "shield_blocks_a_hit_from_the_front_and_not_from_behind";
    private static final String BASHES_THE_MOB_IN_FRONT = "shield_bashes_the_mob_in_front";
    private static final String NO_BASH_WITHOUT_IT = "no_bash_without_the_shield_raised";
    private static final String PAYS_FOR_WHAT_IT_DOES = "shield_pays_for_what_it_blocks_and_bashes_and_not_time";
    private static final Identifier SHIELD = Identifier.parse("goo:durable_shield");
    private static final String ABILITY_REQUIRED = "goo:durable_shield must be loaded";
    /** Two blocks east of the caster, the way they face, inside reach. */
    private static final BlockPos IN_FRONT = new BlockPos(4, 1, 2);
    /** Two blocks west of the caster, at their back. */
    private static final BlockPos BEHIND = new BlockPos(0, 1, 2);
    private static final float FACING_EAST = -90f;
    private static final double REACH = 6;
    private static final int CASTER_GOO = 100 * GooStacks.THOUSAND;
    /** Ticks the shield is raised before the first hit lands. */
    private static final int RAISED_TICKS = 5;
    private static final int IDLE_TICKS = 100;
    /** Past the half second a hit leaves its target unhurtable. */
    private static final int PAST_INVULNERABLE = 21;
    /** A hit the striker deals, unscaled by difficulty. */
    private static final float HIT = 4f;
    private static final String FRONT_HURT = "A hit from the front should land on the shield, the caster lost %s";
    private static final String BACK_SPARED = "A hit from behind should land, the caster lost %s";
    private static final String BASH_DAMAGE_WRONG = "The bash should deal %s, the mob lost %s";
    private static final String NOT_KNOCKED_BACK = "The bash should knock the mob away east, its motion is %s";
    private static final String BASHED_UNRAISED = "With no shield raised no bash should land, the mob lost %s";
    private static final String SPENT_WRONG = "%s should spend %d durable goo, spent %d";

    private ShieldTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BLOCKS_FROM_THE_FRONT, ShieldTests::blocksAHitFromTheFrontAndNotFromBehind);
        GooTestFunctions.registerTest(registrar, BASHES_THE_MOB_IN_FRONT, ShieldTests::bashesTheMobInFront);
        GooTestFunctions.registerTest(registrar, NO_BASH_WITHOUT_IT, ShieldTests::noBashWithoutTheShieldRaised);
        GooTestFunctions.registerTest(registrar, PAYS_FOR_WHAT_IT_DOES, ShieldTests::paysForWhatItBlocksAndBashes);
    }

    /**
     * A zombie's hit on the caster holding Shield takes no health from the
     * front, and the same zombie's hit from behind takes health.
     *
     * @param helper the gametest helper
     */
    public static void blocksAHitFromTheFrontAndNotFromBehind(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(IN_FRONT));
        holdFor(helper, caster, RAISED_TICKS + PAST_INVULNERABLE + 1);
        helper.runAfterDelay(RAISED_TICKS, () -> {
            float before = caster.getHealth();
            zombie.doHurtTarget(helper.getLevel(), caster);
            helper.assertTrue(caster.getHealth() == before, String.format(FRONT_HURT, before - caster.getHealth()));
            Vec3 behind = Vec3.atBottomCenterOf(helper.absolutePos(BEHIND));
            zombie.teleportTo(behind.x, behind.y, behind.z);
        });
        helper.runAfterDelay(RAISED_TICKS + PAST_INVULNERABLE, () -> {
            float beforeBehind = caster.getHealth();
            zombie.doHurtTarget(helper.getLevel(), caster);
            helper.assertTrue(caster.getHealth() < beforeBehind,
                    String.format(BACK_SPARED, beforeBehind - caster.getHealth()));
            done(helper, caster);
        });
    }

    /**
     * A left click on the pig in front while Shield is raised takes the bash
     * damage durable_shield.json names from it, a pig wearing no armor to
     * soak it, and knocks it east, away from the caster.
     *
     * @param helper the gametest helper
     */
    public static void bashesTheMobInFront(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        ShieldStep shield = shieldOf(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, Vec3.atBottomCenterOf(IN_FRONT));
        holdFor(helper, caster, RAISED_TICKS + 1);
        helper.runAfterDelay(RAISED_TICKS, () -> {
            float before = pig.getHealth();
            caster.attack(pig);
            float lost = before - pig.getHealth();
            helper.assertTrue(Math.abs(lost - shield.bashDamage()) < 1e-3,
                    String.format(BASH_DAMAGE_WRONG, shield.bashDamage(), lost));
            helper.assertTrue(pig.getDeltaMovement().x > 0,
                    String.format(NOT_KNOCKED_BACK, pig.getDeltaMovement()));
            done(helper, caster);
        });
    }

    /**
     * A bare-handed left click on the zombie in front with no Shield raised
     * takes no bash's damage from it.
     *
     * @param helper the gametest helper
     */
    public static void noBashWithoutTheShieldRaised(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        ShieldStep shield = shieldOf(helper);
        caster.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(IN_FRONT));
        helper.runAfterDelay(RAISED_TICKS, () -> {
            float before = zombie.getHealth();
            caster.attack(zombie);
            float lost = before - zombie.getHealth();
            helper.assertTrue(lost < shield.bashDamage(), String.format(BASHED_UNRAISED, lost));
            done(helper, caster);
        });
    }

    /**
     * Shield raised a hundred ticks with nothing striking it spends no
     * durable goo; a hit it blocks spends the JSON's cost for each point
     * blocked; a bash spends the JSON's cost for a bash.
     *
     * @param helper the gametest helper
     */
    public static void paysForWhatItBlocksAndBashes(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        ShieldStep shield = shieldOf(helper);
        ServerPlayer striker = striker(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(IN_FRONT));
        int[] held = new int[1];
        holdFor(helper, caster, IDLE_TICKS + RAISED_TICKS + 2);
        helper.runAfterDelay(RAISED_TICKS, () -> held[0] = durableHeld(caster));
        helper.runAfterDelay(RAISED_TICKS + IDLE_TICKS, () -> {
            assertSpent(helper, "A hundred idle ticks", 0, held[0] - durableHeld(caster));
            int beforeHit = durableHeld(caster);
            caster.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(striker), HIT);
            assertSpent(helper, "A blocked hit", (int) Math.ceil(HIT * shield.costPerPointBlocked()),
                    beforeHit - durableHeld(caster));
            int beforeBash = durableHeld(caster);
            caster.attack(zombie);
            assertSpent(helper, "A bash", shield.bashCost(), beforeBash - durableHeld(caster));
            helper.getLevel().getServer().getPlayerList().remove(striker);
            done(helper, caster);
        });
    }

    private static void assertSpent(GameTestHelper helper, String what, int expected, int spent) {
        helper.assertTrue(spent == expected, String.format(SPENT_WRONG, what, expected, spent));
    }

    private static int durableHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.DURABLE, 0);
    }

    private static ShieldStep shieldOf(GameTestHelper helper) {
        AbilityDefinition shield = AbilityRegistry.of(helper.getLevel()).getAbility(SHIELD);
        helper.assertTrue(shield != null, ABILITY_REQUIRED);
        return ShieldStep.in(shield.behaviors()).orElseThrow();
    }

    // Holds Shield as the client's hold does, a stream tick each game tick.
    private static void holdFor(GameTestHelper helper, ServerPlayer player, int ticks) {
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, holdTick(player)));
        }
    }

    private static GooStreamPayload holdTick(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.DURABLE), SHIELD.toString(), eye,
                eye.add(player.getLookAngle().scale(REACH)));
    }

    // A survival caster, since vanilla's mock player stands in creative and takes no mob's hit.
    private static ServerPlayer caster(GameTestHelper helper) {
        AbilityDefinition shield = AbilityRegistry.of(helper.getLevel()).getAbility(SHIELD);
        helper.assertTrue(shield != null, ABILITY_REQUIRED);
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        player.setYRot(FACING_EAST);
        player.setYHeadRot(FACING_EAST);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.DURABLE, CASTER_GOO));
        KnownRecipes.teachRequires(player, shield);
        return player;
    }

    // A second player in front of the caster, whose hit scales with no difficulty.
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer striker(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(IN_FRONT)).add(0, 0, 1);
        player.setPos(at.x, at.y, at.z);
        return player;
    }

    private static void done(GameTestHelper helper, ServerPlayer caster) {
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.succeed();
    }
}
