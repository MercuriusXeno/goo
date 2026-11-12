package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.DamageStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for nether decay: a held stream of gnats paints the stone it
 * reaches, which keeps stepping toward cobblestone while the hold lasts
 * though the aim has left it, finishes alone once past nine tenths of its step even
 * after release, and within one hold steps no further than cobblestone
 * (decision decay-gnats-degrade-each-block-once).
 */
@RegistersGameTests
public final class DecayStreamTests {
    private static final String DECAY_PAINTED_STEPS_UNAIMED = "decay_painted_block_steps_unaimed";
    private static final String DECAY_PAST_NINE_TENTHS_FINISHES = "decay_past_nine_tenths_finishes_after_release";
    private static final String DECAY_STEPS_THROUGH_A_BITE = "decay_painted_block_steps_through_a_bite";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DECAY_PAINTED_STEPS_UNAIMED, DecayStreamTests::decayPaintedBlockStepsUnaimed);
        GooTestFunctions.registerTest(registrar, DECAY_PAST_NINE_TENTHS_FINISHES,
                DecayStreamTests::decayPastNineTenthsFinishesAfterRelease);
        GooTestFunctions.registerTest(registrar, DECAY_STEPS_THROUGH_A_BITE, DecayStreamTests::decayPaintedBlockStepsThroughABite);
        GooTestFunctions.registerTest(registrar, DECAY_HEALS_ZOMBIE, DecayStreamTests::decayHealsAWoundedZombie);
        GooTestFunctions.registerTest(registrar, DECAY_HEALS_UNDEAD_CASTER, DecayStreamTests::decayHealsItsUndeadCaster);
        GooTestFunctions.registerTest(registrar, DECAY_SPARES_LIVING_CASTER, DecayStreamTests::decaySparesItsLivingCaster);
    }

    /**
     * A mock player holds decay on a wounded zombie: the swarm heals the
     * zombie by a bite's amount each bite, and no faster than its bites land
     * on a living mob, once each half second of i-frames.
     * decay-and-hive-heal-an-undead-target
     *
     * @param helper the gametest helper
     */
    public static void decayHealsAWoundedZombie(GameTestHelper helper) {
        BlockPos zombiePos = TARGET_POS.east();
        helper.setBlock(zombiePos.below(), Blocks.BEDROCK);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, zombiePos);
        // the helmet keeps the noon sun off the zombie, so only the swarm touches its health
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setHealth(WOUNDED_ZOMBIE_HEALTH);
        ServerPlayer player = decayer(helper);
        player.setXRot(LOOKING_AT_VINDICATOR);
        float bite = biteAmount(helper);
        hold(helper, player, 1, FINISH_TICKS);
        helper.runAfterDelay(FINISH_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float healed = zombie.getHealth() - WOUNDED_ZOMBIE_HEALTH;
            float most = bite * bitesWithin(FINISH_TICKS);
            helper.assertTrue(healed > 0 && healed <= most + HEALTH_SLACK,
                    String.format(SHOULD_HEAL_AT_THE_BITE, healed, most));
            helper.succeed();
        });
    }

    /**
     * A wounded player under nether's Undead holds decay at the open sky:
     * the spray takes its caster in, and its bite heals the undead caster.
     * undead-player-counts-as-undead-for-heal-and-decay
     *
     * @param helper the gametest helper
     */
    public static void decayHealsItsUndeadCaster(GameTestHelper helper) {
        ServerPlayer undead = UndeadTests.undeadAt(helper, STAND_POS);
        float health = holdDecayWounded(helper, undead);
        helper.runAfterDelay(FINISH_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(undead);
            helper.assertTrue(undead.getHealth() > health,
                    String.format(SHOULD_HEAL_UNDEAD_CASTER, health, undead.getHealth()));
            helper.succeed();
        });
    }

    /**
     * A wounded living survival player holds decay at the open sky: the
     * spray takes its caster in and bites it not at all.
     * undead-player-counts-as-undead-for-heal-and-decay
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void decaySparesItsLivingCaster(GameTestHelper helper) {
        ServerPlayer living = helper.makeMockServerPlayerInLevel();
        // the mock player helper makes a creative player, whom no hit lands on
        living.setGameMode(GameType.SURVIVAL);
        // a player whose client has not reported loaded is invulnerable, and no mock client reports
        living.connection.markClientLoaded();
        float health = holdDecayWounded(helper, living);
        helper.runAfterDelay(FINISH_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(living);
            helper.assertTrue(living.getHealth() >= health,
                    String.format(SHOULD_SPARE_LIVING_CASTER, health, living.getHealth()));
            helper.succeed();
        });
    }

    /**
     * Wounds a caster, arms it with decay, stands it facing the open sky so
     * the spray strikes no mob, and holds decay from it.
     *
     * @param helper the gametest helper
     * @param caster the player to hold decay
     * @return the health the caster was wounded to
     */
    private static float holdDecayWounded(GameTestHelper helper, ServerPlayer caster) {
        AbilityDefinition decay = AbilityRegistry.of(helper.getLevel()).getAbility(NETHER_DECAY);
        helper.assertTrue(decay != null, ABILITY_REQUIRED);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        caster.setPos(stand.x, stand.y, stand.z);
        caster.setYRot(FACING_EAST);
        caster.setXRot(LOOKING_AT_SKY);
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.NETHER, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(caster, decay);
        // a fed player regenerates, which would heal the caster whatever the spray does
        caster.getFoodData().setFoodLevel(UNFED_FOOD);
        caster.setHealth(WOUNDED_PLAYER_HEALTH);
        hold(helper, caster, 1, FINISH_TICKS);
        return WOUNDED_PLAYER_HEALTH;
    }

    /**
     * The health one of Decay's bites takes, read from the spray's damage step.
     *
     * @param helper the gametest helper
     * @return the bite's amount
     */
    private static float biteAmount(GameTestHelper helper) {
        AbilityDefinition decay = AbilityRegistry.of(helper.getLevel()).getAbility(NETHER_DECAY);
        helper.assertTrue(decay != null, ABILITY_REQUIRED);
        return decay.behaviors().stream().filter(DamageStep.class::isInstance).map(DamageStep.class::cast)
                .findFirst().orElseThrow().amount().evaluateFloat(Variables.NONE);
    }


    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** Three blocks east of the player, at its height, on a block the test lays. */
    private static final BlockPos TARGET_POS = STAND_POS.east(3);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the block's center three blocks off. */
    private static final float LOOKING_AT_BLOCK = 20.5f;
    /** Pitch up into open air, the cone well clear of the stone. */
    private static final float LOOKING_AT_SKY = -80f;
    private static final int HELD_GOO = 6;
    /** nether_decay.json's degrade ticks: fifteen ticks of swarm step a block. */
    private static final int STEP_TICKS = 15;
    /** A brush across the stone short of half its step. */
    private static final int BRUSH_TICKS = 3;
    /** Aim on the stone past nine tenths of its step, short of the whole. */
    private static final int PAST_NINE_TENTHS_TICKS = 14;
    /** Room for a block to finish its step, and then some. */
    private static final int FINISH_TICKS = STEP_TICKS + 10;
    private static final Identifier NETHER_DECAY = Identifier.parse("goo:nether_decay");
    private static final String ABILITY_REQUIRED = "Ability registry must hold nether_decay";
    private static final String SHOULD_BITE = "Decay's swarm should bite the vindicator in its cone";
    private static final String DECAY_HEALS_ZOMBIE = "decay_heals_a_wounded_zombie";
    private static final String DECAY_HEALS_UNDEAD_CASTER = "decay_heals_its_undead_caster";
    private static final String DECAY_SPARES_LIVING_CASTER = "decay_spares_its_living_caster";
    /** Under the eighteen food a player regenerates at, and above starving. */
    private static final int UNFED_FOOD = 10;
    /** A zombie's twenty less sixteen, room for a heal every tick to show past the bites' pace. */
    private static final float WOUNDED_ZOMBIE_HEALTH = 4f;
    /** Half a player's twenty, room to heal. */
    private static final float WOUNDED_PLAYER_HEALTH = 10f;
    private static final String SHOULD_HEAL_AT_THE_BITE =
            "Decay's swarm should heal the zombie, at most a bite each half second; healed %.2f of at most %.2f";
    private static final String SHOULD_HEAL_UNDEAD_CASTER =
            "An undead player's own decay should heal it from %.1f, stands at %.2f";
    private static final String SHOULD_SPARE_LIVING_CASTER =
            "A living player's own decay should not harm it from %.1f, stands at %.2f";
    /** Vanilla's i-frames: a mob hit takes no hit of the same size again for half a second. */
    private static final int I_FRAME_TICKS = 10;
    private static final float HEALTH_SLACK = 0.01f;
    /** A vindicator stands two blocks out, in the cone the player aims at the stone three out. */
    private static final BlockPos IN_THE_CONE = STAND_POS.east(2);
    /**
     * Pitch down onto the middle of a vindicator four blocks off; the stone five
     * off, behind it, lies under three degrees from that line, inside the cone.
     */
    private static final float LOOKING_AT_VINDICATOR = 9.2f;

    private DecayStreamTests() {
    }

    /**
     * A mock player brushes decay across stone for a few ticks, then turns
     * its aim to the sky and keeps holding: the painted stone steps to
     * cobblestone with the aim off it.
     *
     * @param helper the gametest helper
     */
    public static void decayPaintedBlockStepsUnaimed(GameTestHelper helper) {
        ServerPlayer player = decayerOverStone(helper);
        hold(helper, player, 1, BRUSH_TICKS + FINISH_TICKS);
        helper.runAfterDelay(BRUSH_TICKS + 1, () -> player.setXRot(LOOKING_AT_SKY));
        helper.runAfterDelay(BRUSH_TICKS + FINISH_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS);
            helper.succeed();
        });
    }

    /**
     * A mock player holds decay on stone past nine tenths of its step and lets go:
     * the stone finishes its step to cobblestone on its own.
     *
     * @param helper the gametest helper
     */
    public static void decayPastNineTenthsFinishesAfterRelease(GameTestHelper helper) {
        ServerPlayer player = decayerOverStone(helper);
        hold(helper, player, 1, PAST_NINE_TENTHS_TICKS);
        helper.runAfterDelay(PAST_NINE_TENTHS_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.STONE, TARGET_POS);
        });
        helper.runAfterDelay(PAST_NINE_TENTHS_TICKS + FINISH_TICKS, () -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS);
            helper.succeed();
        });
    }

    /**
     * A mock player brushes decay across stone for a few ticks, short of half
     * its step, then a vindicator steps into the cone and the swarm bites it while
     * the hold goes on: the painted stone keeps stepping to cobblestone
     * through the bite (the operator's ruling on decision
     * decay-gnats-degrade-each-block-once).
     *
     * @param helper the gametest helper
     */
    public static void decayPaintedBlockStepsThroughABite(GameTestHelper helper) {
        ServerPlayer player = decayerOverStone(helper);
        helper.setBlock(IN_THE_CONE.below(), Blocks.BEDROCK);
        hold(helper, player, 1, BRUSH_TICKS + FINISH_TICKS);
        Mob[] vindicator = new Mob[1];
        helper.runAfterDelay(BRUSH_TICKS + 1, () -> {
            vindicator[0] = helper.spawnWithNoFreeWill(EntityType.VINDICATOR, IN_THE_CONE);
        });
        helper.runAfterDelay(BRUSH_TICKS + FINISH_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(vindicator[0].getHealth() < vindicator[0].getMaxHealth(), SHOULD_BITE);
            helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS);
            helper.succeed();
        });
    }

    /**
     * How many bites land in a hold: the first tick's, then one each time the
     * mob's i-frames run out.
     *
     * @param ticks the ticks of the hold
     * @return the bites that land
     */
    private static int bitesWithin(int ticks) {
        return (ticks + I_FRAME_TICKS - 1) / I_FRAME_TICKS;
    }

    /**
     * Lays stone on bedrock at the target and stands a decaying player aimed at it.
     *
     * @param helper the gametest helper
     * @return the player
     */
    private static ServerPlayer decayerOverStone(GameTestHelper helper) {
        helper.setBlock(TARGET_POS.below(), Blocks.BEDROCK);
        helper.setBlock(TARGET_POS, Blocks.STONE);
        return decayer(helper);
    }

    /**
     * Streams decay every tick of a hold, aimed wherever the player looks that tick.
     *
     * @param helper the gametest helper
     * @param player the streaming player
     * @param from   the first tick of the hold
     * @param ticks  how many ticks it lasts
     */
    private static void hold(GameTestHelper helper, ServerPlayer player, int from, int ticks) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.NETHER), NETHER_DECAY.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 0; held < ticks; held++) {
            helper.runAfterDelay(from + held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer decayer(GameTestHelper helper) {
        AbilityDefinition decay = AbilityRegistry.of(helper.getLevel()).getAbility(NETHER_DECAY);
        helper.assertTrue(decay != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_AT_BLOCK);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.NETHER, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, decay);
        return player;
    }
}
