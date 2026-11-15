package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityPrice;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.petrify.Petrification;
import com.mercuriusxeno.goo.ability.petrify.Statues;
import com.mercuriusxeno.goo.ability.program.CalcifyStep;
import com.mercuriusxeno.goo.ability.program.PetrifyStep;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * Gametests for rock petrify: a held stream fills a penned zombie's gauge,
 * slowing it, until it becomes a statue block; a gauge the fog leaves drains
 * back; gravel under the fog builds toward cobblestone, regressing when let
 * go short of it and stepping once held long enough; and a statue mines like
 * cobblestone for cobblestone and the zombie's experience
 * (decision petrify-stone-encasement-and-calcify-map).
 */
@RegistersGameTests
public final class PetrifyStreamTests {
    private static final String PETRIFY_SLOWS_THEN_STATUES = "petrify_slows_then_statues";
    private static final String PETRIFY_GAUGE_DRAINS = "petrify_gauge_drains";
    private static final String PETRIFY_CALCIFIES_GRADUALLY = "petrify_calcifies_gradually";
    private static final String STATUE_MINES = "statue_mines_for_cobblestone_and_experience";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PETRIFY_SLOWS_THEN_STATUES, PetrifyStreamTests::petrifySlowsThenStatues);
        GooTestFunctions.registerTest(registrar, PETRIFY_GAUGE_DRAINS, PetrifyStreamTests::petrifyGaugeDrains);
        GooTestFunctions.registerTest(registrar, PETRIFY_CALCIFIES_GRADUALLY, PetrifyStreamTests::petrifyCalcifiesGradually);
        GooTestFunctions.registerTest(registrar, STATUE_MINES, PetrifyStreamTests::statueMinesForCobblestoneAndExperience);
        GooTestFunctions.registerTest(registrar, PETRIFY_PAYS_PER_BLOCK,
                PetrifyStreamTests::petrifyPaysABlocksPricePerBlockCalcified);
        GooTestFunctions.registerTest(registrar, PETRIFY_PAYS_PER_POINT,
                PetrifyStreamTests::petrifyPaysAGaugePricePerPointFilled);
        GooTestFunctions.registerTest(registrar, PETRIFY_PAYS_NOTHING_DOING_NOTHING,
                PetrifyStreamTests::petrifyPaysNothingCalcifyingAndFillingNothing);
        GooTestFunctions.registerTest(registrar, PETRIFY_CALCIFIES_NO_UNPAID_BLOCK,
                PetrifyStreamTests::petrifyCalcifiesNoBlockItCannotPayFor);
    }

    private static final String PETRIFY_PAYS_PER_BLOCK = "petrify_pays_a_blocks_price_per_block_calcified";
    private static final String PETRIFY_PAYS_PER_POINT = "petrify_pays_a_gauge_price_per_point_filled";
    private static final String PETRIFY_PAYS_NOTHING_DOING_NOTHING =
            "petrify_pays_nothing_calcifying_and_filling_nothing";
    private static final String PETRIFY_CALCIFIES_NO_UNPAID_BLOCK =
            "petrify_pays_for_and_calcifies_no_block_it_cannot_afford";
    /** Two blocks north of the target, a second gravel the look turns onto, outside the cone aimed at the first. */
    private static final BlockPos SECOND_TARGET_POS = new BlockPos(3, 1, 0);
    /** Pitch up into open air, the cone well clear of every block. */
    private static final float LOOKING_AT_SKY = -80f;
    /** Three seconds of a hold at the open sky. */
    private static final int OPEN_AIR_TICKS = 60;
    /** A second at the open sky before the gravel, what a hold paying by the tick would spend one price on. */
    private static final int SKY_LEAD_IN_TICKS = 20;
    /** Ticks past a rung's step a block is given to finish it. */
    private static final int FINISH_MARGIN = 10;
    /** Ticks after a hold ends before its reading, so a last tick's charge stands. */
    private static final int READ_DELAY_TICKS = 3;
    /** The most a hold paying exactly for its points drains past them: the part of a mb the remainder banks. */
    private static final double BANKED_PART_OF_A_MB = 1;
    private static final String SHOULD_CALCIFY = "Petrify should calcify at least one of the two gravels";
    private static final String SHOULD_FILL = "Petrify should fill the zombie's gauge";
    private static final String SHOULD_RUN_OUT = "The player should hold no rock goo, holds ";
    private static final String DRAINED_WRONG = "The hold should drain %s mb of rock goo, drained %d";

    /**
     * A Petrify hold with goo to spare calcifies one gravel, then turns onto
     * a second and calcifies it: the player's rock goo falls by the block
     * price for each block calcified, and nothing for the ticks held.
     * petrify-pays-per-block-calcified-and-gauge-point
     *
     * @param helper the gametest helper
     */
    public static void petrifyPaysABlocksPricePerBlockCalcified(GameTestHelper helper) {
        layGravel(helper, TARGET_POS);
        layGravel(helper, SECOND_TARGET_POS);
        ServerPlayer player = petrifier(helper, LOOKING_AT_GRAVEL);
        int before = rockHeld(player);
        int ended = holdOnEachInTurn(helper, player, 1);
        helper.runAfterDelay(ended, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            int calcified = cobbled(helper, TARGET_POS) + cobbled(helper, SECOND_TARGET_POS);
            helper.assertTrue(calcified > 0, SHOULD_CALCIFY);
            double owed = blockPrice(helper).times(calcified).toDouble();
            assertDrainedNear(helper, owed, before - rockHeld(player));
            helper.succeed();
        });
    }

    /**
     * A Petrify hold on a penned zombie, short of a statue, fills its stone
     * gauge: the player's rock goo falls by the gauge price for each point
     * filled, read off the gauge before and after.
     * petrify-pays-per-block-calcified-and-gauge-point
     *
     * @param helper the gametest helper
     */
    public static void petrifyPaysAGaugePricePerPointFilled(GameTestHelper helper) {
        Mob zombie = pennedZombie(helper);
        ServerPlayer player = petrifier(helper, LOOKING_AT_ZOMBIE);
        int before = rockHeld(player);
        float gaugeBefore = zombie.getData(GooAttachments.PETRIFICATION).gauge();
        hold(helper, player, 1, PART_HOLD_TICKS);
        helper.runAfterDelay(PART_HOLD_TICKS + READ_DELAY_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float filled = zombie.getData(GooAttachments.PETRIFICATION).gauge() - gaugeBefore;
            helper.assertTrue(filled > 0, SHOULD_FILL);
            assertDrainedNear(helper, pointPrice(helper).toDouble() * filled, before - rockHeld(player));
            helper.succeed();
        });
    }

    /**
     * A Petrify hold aimed at the open sky for several seconds calcifies no
     * block and fills no gauge, and leaves the player's rock goo as it
     * started.
     * petrify-pays-per-block-calcified-and-gauge-point
     *
     * @param helper the gametest helper
     */
    public static void petrifyPaysNothingCalcifyingAndFillingNothing(GameTestHelper helper) {
        ServerPlayer player = petrifier(helper, LOOKING_AT_SKY);
        int before = rockHeld(player);
        hold(helper, player, 1, OPEN_AIR_TICKS);
        helper.runAfterDelay(OPEN_AIR_TICKS + READ_DELAY_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            int drained = before - rockHeld(player);
            helper.assertTrue(drained == 0, String.format(DRAINED_WRONG, 0, drained));
            helper.succeed();
        });
    }

    /**
     * A player holding exactly one block price holds Petrify at the open sky
     * for a second, then on one gravel and then a second: the first
     * calcifies, the second stays gravel, and the rock goo runs out. The
     * second at the sky first spends what a hold paying by the tick would, so
     * the one price reaches the gravel only where nothing is charged for time.
     * petrify-pays-per-block-calcified-and-gauge-point
     *
     * @param helper the gametest helper
     */
    public static void petrifyCalcifiesNoBlockItCannotPayFor(GameTestHelper helper) {
        layGravel(helper, TARGET_POS);
        layGravel(helper, SECOND_TARGET_POS);
        ServerPlayer player = petrifier(helper, LOOKING_AT_SKY);
        GooSourceScanner.deplete(player, GooTypes.ROCK, rockHeld(player) - blockPrice(helper).ceiling());
        hold(helper, player, 1, SKY_LEAD_IN_TICKS);
        helper.runAfterDelay(SKY_LEAD_IN_TICKS, () -> player.setXRot(LOOKING_AT_GRAVEL));
        int ended = SKY_LEAD_IN_TICKS + holdOnEachInTurn(helper, player, SKY_LEAD_IN_TICKS + 1);
        helper.runAfterDelay(ended, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS);
            helper.assertBlockPresent(Blocks.GRAVEL, SECOND_TARGET_POS);
            helper.assertTrue(rockHeld(player) == 0, SHOULD_RUN_OUT + rockHeld(player));
            helper.succeed();
        });
    }

    /**
     * Holds Petrify on the gravel at the target long enough to step it a
     * rung, then turns the look onto the second gravel and holds as long
     * again.
     *
     * @param helper the gametest helper
     * @param player the petrifying player, aimed at the target
     * @param from   the first tick of the hold
     * @return the ticks from the hold's start to its reading, the second gravel given time to step
     */
    private static int holdOnEachInTurn(GameTestHelper helper, ServerPlayer player, int from) {
        int finish = rungTicks(helper) + FINISH_MARGIN;
        hold(helper, player, from, finish * 2);
        helper.runAfterDelay(from + finish, () -> player.lookAt(EntityAnchorArgument.Anchor.EYES,
                Vec3.atCenterOf(helper.absolutePos(SECOND_TARGET_POS))));
        return finish * 2 + READ_DELAY_TICKS;
    }

    private static void layGravel(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, Blocks.GRAVEL);
    }

    private static int cobbled(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).is(Blocks.COBBLESTONE) ? 1 : 0;
    }

    private static AbilityDefinition petrify(GameTestHelper helper) {
        AbilityDefinition petrify = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_PETRIFY);
        helper.assertTrue(petrify != null, ABILITY_REQUIRED);
        return petrify;
    }

    /**
     * The ticks of fog a block takes to step a rung, read live from Petrify's calcify step.
     *
     * @param helper the gametest helper
     * @return the step's ticks
     */
    private static int rungTicks(GameTestHelper helper) {
        return petrify(helper).behaviors().stream().filter(CalcifyStep.class::isInstance).map(CalcifyStep.class::cast)
                .findFirst().orElseThrow().ticks();
    }

    /**
     * Petrify's price for each block it calcifies, its cost, read from the loaded definition.
     *
     * @param helper the gametest helper
     * @return the exact price in mb
     */
    private static GooFraction blockPrice(GameTestHelper helper) {
        return petrify(helper).cost().exactIn(GooValues.of(helper.getLevel()));
    }

    /**
     * Petrify's price for each point of a mob's stone gauge it fills, its petrify step's point cost, read from the
     * loaded definition.
     *
     * @param helper the gametest helper
     * @return the exact price in mb
     */
    private static GooFraction pointPrice(GameTestHelper helper) {
        PetrifyStep fill = petrify(helper).behaviors().stream().filter(PetrifyStep.class::isInstance)
                .map(PetrifyStep.class::cast).findFirst().orElseThrow();
        return AbilityPrice.exactUnitPrice(fill.pointCost());
    }

    private static int rockHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ROCK, 0);
    }

    /**
     * Asserts a hold drained what it owes, short of a whole mb past it, the
     * part of a mb overpaid banked in the player's remainder.
     *
     * @param helper  the gametest helper
     * @param owed    the mb the hold owes, exact or as the gauge's floats read it
     * @param drained the mb the hold drained
     */
    private static void assertDrainedNear(GameTestHelper helper, double owed, int drained) {
        helper.assertTrue(drained >= owed - BANKED_PART_OF_A_MB && drained <= owed + BANKED_PART_OF_A_MB,
                String.format(DRAINED_WRONG, owed, drained));
    }


    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** Three blocks east of the player, at its height, on a block the test lays. */
    private static final BlockPos TARGET_POS = STAND_POS.east(3);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the zombie's middle three blocks off. */
    private static final float LOOKING_AT_ZOMBIE = 12f;
    /** Pitch down from the eye toward the gravel's center three blocks off. */
    private static final float LOOKING_AT_GRAVEL = 20.5f;
    /** Rock goo the player holds, thousands, enough to stream the longest hold. */
    private static final int HELD_GOO = 10;
    /** Partway into a hold, short of any fill that statues the zombie. */
    private static final int PART_HOLD_TICKS = 25;
    /** The most ticks a hold runs while the test waits for the statue, well past the fill. */
    private static final int STATUE_HOLD_CAP = 150;
    /** The most ticks a hold runs while the test waits for the gravel to step, several rungs. */
    private static final int RUNG_HOLD_CAP = 120;
    /** Ticks for a released share to decay back to its floor and then some. */
    private static final int SETTLE_TICKS = 60;
    private static final double ITEM_SEARCH_RADIUS = 3;
    private static final Identifier ROCK_PETRIFY = Identifier.parse("goo:rock_petrify");
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_petrify";
    private static final String SHOULD_SLOW = "A part petrified zombie should be slower than %s, moves %s";
    private static final String SHOULD_LEAVE = "The petrified zombie should be gone into its statue";
    private static final String SHOULD_DRAIN = "A gauge the fog left should drain from %s, stands %s";
    private static final String SHOULD_DROP_EXPERIENCE = "A mined statue should drop experience";

    private PetrifyStreamTests() {
    }

    /**
     * A mock player holds petrify on a penned zombie: partway through it is
     * slower than it stood, and once its gauge fills a statue stands where it
     * stood and the zombie is gone.
     *
     * @param helper the gametest helper
     */
    public static void petrifySlowsThenStatues(GameTestHelper helper) {
        Mob zombie = pennedZombie(helper);
        double baseSpeed = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
        ServerPlayer player = petrifier(helper, LOOKING_AT_ZOMBIE);
        hold(helper, player, 1, STATUE_HOLD_CAP);
        helper.runAfterDelay(PART_HOLD_TICKS + 1, () -> {
            double speed = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(speed < baseSpeed, String.format(SHOULD_SLOW, baseSpeed, speed));
        });
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(GooBlocks.STATUE.get(), TARGET_POS);
            helper.assertTrue(zombie.isRemoved(), SHOULD_LEAVE);
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A mock player holds petrify on a penned zombie partway, then lets go:
     * once the fog has left it a while its gauge drains below where it stood.
     *
     * @param helper the gametest helper
     */
    public static void petrifyGaugeDrains(GameTestHelper helper) {
        Mob zombie = pennedZombie(helper);
        ServerPlayer player = petrifier(helper, LOOKING_AT_ZOMBIE);
        hold(helper, player, 1, PART_HOLD_TICKS);
        float[] atRelease = new float[1];
        helper.runAfterDelay(PART_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            atRelease[0] = zombie.getData(GooAttachments.PETRIFICATION).gauge();
        });
        helper.runAfterDelay(PART_HOLD_TICKS + SETTLE_TICKS, () -> {
            Petrification now = zombie.getData(GooAttachments.PETRIFICATION);
            helper.assertTrue(now.gauge() < atRelease[0], String.format(SHOULD_DRAIN, atRelease[0], now.gauge()));
            helper.succeed();
        });
    }

    /**
     * A mock player holds petrify on gravel until it steps a rung, to
     * cobblestone, whatever the rung's ticks.
     *
     * @param helper the gametest helper
     */
    public static void petrifyCalcifiesGradually(GameTestHelper helper) {
        helper.setBlock(TARGET_POS.below(), Blocks.STONE);
        helper.setBlock(TARGET_POS, Blocks.GRAVEL);
        ServerPlayer player = petrifier(helper, LOOKING_AT_GRAVEL);
        hold(helper, player, 1, RUNG_HOLD_CAP);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS);
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A zombie turned to a statue, mined by a survival player with a pickaxe,
     * leaves cobblestone and experience behind.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void statueMinesForCobblestoneAndExperience(GameTestHelper helper) {
        Mob zombie = pennedZombie(helper);
        ServerPlayer miner = helper.makeMockServerPlayerInLevel();
        miner.setGameMode(GameType.SURVIVAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        miner.setPos(stand.x, stand.y, stand.z);
        miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        helper.runAfterDelay(1, () -> {
            Statues.encase(helper.getLevel(), zombie, miner);
            helper.assertBlockPresent(GooBlocks.STATUE.get(), TARGET_POS);
            miner.gameMode.destroyBlock(helper.absolutePos(TARGET_POS));
        });
        helper.runAfterDelay(2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(miner);
            helper.assertBlockPresent(Blocks.AIR, TARGET_POS);
            helper.assertItemEntityPresent(Items.COBBLESTONE, TARGET_POS, ITEM_SEARCH_RADIUS);
            helper.assertTrue(!helper.getEntities(EntityType.EXPERIENCE_ORB, TARGET_POS, ITEM_SEARCH_RADIUS).isEmpty(),
                    SHOULD_DROP_EXPERIENCE);
            helper.succeed();
        });
    }

    /**
     * Floors the target cell with stone, rings it with glass two high so the
     * zombie in it stays in the cone, and stands a helmeted zombie there; the
     * stream strikes the entities in its cone whatever stands between.
     *
     * @param helper the gametest helper
     * @return the zombie
     */
    private static Mob pennedZombie(GameTestHelper helper) {
        helper.setBlock(TARGET_POS.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(TARGET_POS.relative(side), Blocks.GLASS);
            helper.setBlock(TARGET_POS.relative(side).above(), Blocks.GLASS);
        }
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, TARGET_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    /**
     * Streams petrify every tick of a hold.
     *
     * @param helper the gametest helper
     * @param player the streaming player
     * @param from   the first tick of the hold
     * @param ticks  how many ticks it lasts
     */
    private static void hold(GameTestHelper helper, ServerPlayer player, int from, int ticks) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.ROCK), ROCK_PETRIFY.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 0; held < ticks; held++) {
            helper.runAfterDelay(from + held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer petrifier(GameTestHelper helper, float pitch) {
        AbilityDefinition petrify = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_PETRIFY);
        helper.assertTrue(petrify != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(pitch);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.ROCK, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, petrify);
        return player;
    }
}
