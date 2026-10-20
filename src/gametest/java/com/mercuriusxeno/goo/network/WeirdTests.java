package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.ability.weird.Wobbled;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Weird's abilities, each cast through its real path: a blob
 * through its landing, Coat through the stream tick.
 * weird-bounces-and-softens-harm
 */
@RegistersGameTests
public final class WeirdTests {

    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final Identifier COAT = Identifier.parse("goo:weird_coat");
    private static final String ABILITY_REQUIRED = "Ability registry must hold goo:weird_coat";
    private static final String COAT_COOLS_LAVA = "coat_cools_lava_to_magma";
    private static final String COAT_LAYS_PADS = "coat_lays_a_pad_on_the_floor";
    private static final String SOFTEN_TRADES_DAMAGE = "coat_softens_the_mob_it_hits";
    private static final BlockPos POOL_FLOOR = new BlockPos(2, 0, 2);
    private static final BlockPos STREAMER_POS = new BlockPos(0, 1, 2);
    private static final float FACING_EAST = -90f;
    /** Pitched down at the floor three and four blocks east, so both floor cells stand in Coat's cone. */
    private static final float AT_THE_FLOOR = 32f;
    /** Pitched a little down at a zombie four blocks east. */
    private static final float AT_THE_ZOMBIE = 8f;
    /** Two floor cells three and four blocks east of the streamer, inside Coat's cone pitched at the floor. */
    private static final List<BlockPos> LAVA_CELLS = List.of(new BlockPos(3, 0, 2), new BlockPos(4, 0, 2));
    private static final BlockPos ZOMBIE_POS = new BlockPos(4, 1, 2);
    private static final int COAT_HOLD_TICKS = 10;
    /** The open cells above the floor Coat's cone reaches when pitched at it run along the look from here. */
    private static final int PAD_NEAR_X = 1;
    private static final int PAD_FAR_X = 7;
    private static final String LAVA_LEFT = "Coat's stream should leave magma where the lava stood at ";
    private static final String NO_COAT_PAD = "Coat's stream held on a stone floor should leave a bounce pad on it";
    private static final String BOUNCE = "goo:weird_bounce";
    private static final String PAD_BREAKS_THE_FALL = "weird_pad_breaks_the_fall";
    /** The test bay's roof stands five blocks over the pad, so the pig starts under it. */
    private static final int DROP_START = 3;
    /** A fall a pig at ten health dies of on stone, carried in as the fall it has already made. */
    private static final double FALLEN_BLOCKS = 20;
    private static final double FALL_SPEED = -1.0;
    private static final String NO_PAD = "Weird Bounce landing on stone should leave a bounce pad above it";
    private static final String NOT_LAUNCHED = "A pig falling onto the pad should leave it moving upward";
    private static final String HURT = "A pig falling onto the pad should keep its full health, has ";
    private static final String PAD_STANDS = "The bounce pad should be gone once its time has passed";
    private static final String SLIME = "goo:weird_slime";
    private static final String SLIME_BLOB_MAKES_A_SLIME = "slime_blob_makes_a_slime";
    private static final String NOT_WOBBLED = "A zombie in Coat's stream should be wobbled";
    private static final String HURT_BY_SOFTENED = "A softened zombie's hit should leave the player's health full, has ";
    private static final String NOT_KNOCKED = "A softened zombie's hit should knock the player back";
    private static final String FADED_STILL_SOFT = "Once the wobble has faded, the zombie's hit should hurt the player";

    private WeirdTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, COAT_COOLS_LAVA, WeirdTests::coatCoolsLavaToMagma);
        GooTestFunctions.registerTest(registrar, COAT_LAYS_PADS, WeirdTests::coatLaysAPadOnTheFloor);
        GooTestFunctions.registerTest(registrar, PAD_BREAKS_THE_FALL, WeirdTests::weirdPadBreaksTheFall);
        GooTestFunctions.registerTest(registrar, SOFTEN_TRADES_DAMAGE, WeirdTests::coatSoftensTheMobItHits);
        GooTestFunctions.registerTest(registrar, SLIME_BLOB_MAKES_A_SLIME, WeirdTests::slimeBlobMakesASlime);
    }

    /**
     * A Weird Slime blob lands on stone and a slime stands in the cell it
     * landed in.
     * slime-creates-slimes-at-will
     *
     * @param helper the gametest helper
     */
    public static void slimeBlobMakesASlime(GameTestHelper helper) {
        helper.setBlock(POOL_FLOOR, Blocks.STONE);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.WEIRD,
                NO_ENTITY, helper.absolutePos(POOL_FLOOR), Direction.UP, SLIME));
        helper.assertEntityPresent(EntityType.SLIME, POOL_FLOOR.above());
        helper.succeed();
    }

    /**
     * A zombie in Coat's stream is wobbled and strikes a survival player: the
     * player keeps its full health and is knocked back instead; once the
     * wobble has faded, the zombie's next hit hurts the player.
     *
     * @param helper the gametest helper
     */
    public static void coatSoftensTheMobItHits(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        ServerPlayer streamer = streamerAt(helper, AT_THE_ZOMBIE);
        holdCoat(helper, streamer, () -> {
            helper.assertTrue(zombie.hasData(GooAttachments.WOBBLED), NOT_WOBBLED);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(Vec3.atBottomCenterOf(ZOMBIE_POS.east())));
            player.setDeltaMovement(Vec3.ZERO);
            zombie.doHurtTarget(helper.getLevel(), player);
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), HURT_BY_SOFTENED + player.getHealth());
            helper.assertTrue(player.getDeltaMovement().horizontalDistance() > 0, NOT_KNOCKED);

            zombie.setData(GooAttachments.WOBBLED, new Wobbled(helper.getLevel().getGameTime()));
            player.invulnerableTime = 0;
            zombie.doHurtTarget(helper.getLevel(), player);
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), FADED_STILL_SOFT);
        });
    }

    /**
     * A Weird Bounce blob lands on stone and leaves a pad; a pig falling onto
     * the pad with twenty blocks of fall behind it keeps its full health and
     * leaves the pad moving upward, and the pad is gone once the time
     * weird_bounce.json names has passed.
     *
     * @param helper the gametest helper
     */
    public static void weirdPadBreaksTheFall(GameTestHelper helper) {
        helper.setBlock(POOL_FLOOR, Blocks.STONE);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.WEIRD,
                NO_ENTITY, helper.absolutePos(POOL_FLOOR), Direction.UP, BOUNCE));
        BlockPos pad = POOL_FLOOR.above();
        helper.assertTrue(helper.getBlockState(pad).is(GooBlocks.BOUNCE_PAD.get()), NO_PAD);
        Pig pig = helper.spawn(EntityType.PIG, pad.above(DROP_START));
        pig.fallDistance = FALLEN_BLOCKS;
        pig.setDeltaMovement(0, FALL_SPEED, 0);
        double padTop = helper.absolutePos(pad).getY() + 1;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(pig.getDeltaMovement().y > 0 && pig.getY() >= padTop, NOT_LAUNCHED);
                    helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), HURT + pig.getHealth());
                })
                .thenWaitUntil(() -> helper.assertFalse(helper.getBlockState(pad).is(GooBlocks.BOUNCE_PAD.get()),
                        PAD_STANDS))
                .thenSucceed();
    }

    /**
     * Coat's stream held over two lava cells in a stone floor cools both to
     * magma blocks.
     *
     * @param helper the gametest helper
     */
    public static void coatCoolsLavaToMagma(GameTestHelper helper) {
        for (BlockPos cell : LAVA_CELLS) {
            helper.setBlock(cell, Blocks.LAVA);
        }
        ServerPlayer streamer = streamerAt(helper, AT_THE_FLOOR);
        holdCoat(helper, streamer, () -> {
            for (BlockPos cell : LAVA_CELLS) {
                helper.assertTrue(helper.getBlockState(cell).is(Blocks.MAGMA_BLOCK), LAVA_LEFT + cell);
            }
        });
    }

    /**
     * Coat's stream held on a stone floor leaves a bounce pad standing on
     * it, in an open cell its cone reaches.
     *
     * @param helper the gametest helper
     */
    public static void coatLaysAPadOnTheFloor(GameTestHelper helper) {
        for (BlockPos floor : coneFloor()) {
            helper.setBlock(floor, Blocks.STONE);
        }
        ServerPlayer streamer = streamerAt(helper, AT_THE_FLOOR);
        holdCoat(helper, streamer, () -> {
            boolean padded = false;
            for (BlockPos floor : coneFloor()) {
                padded |= helper.getBlockState(floor.above()).is(GooBlocks.BOUNCE_PAD.get());
            }
            helper.assertTrue(padded, NO_COAT_PAD);
        });
    }

    private static Iterable<BlockPos> coneFloor() {
        return BlockPos.betweenClosed(new BlockPos(PAD_NEAR_X, 0, STREAMER_POS.getZ() - 1),
                new BlockPos(PAD_FAR_X, 0, STREAMER_POS.getZ() + 1));
    }

    /**
     * A Weird invoker standing at the bay's west side facing east, pitched down.
     *
     * @param helper the gametest helper
     * @param pitch  the look's pitch, down from level
     * @return the streamer
     */
    private static ServerPlayer streamerAt(GameTestHelper helper, float pitch) {
        ServerPlayer streamer = SelfDeliveryTests.invoker(helper, GooTypes.WEIRD);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STREAMER_POS));
        streamer.setPos(stand.x, stand.y, stand.z);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(pitch);
        return streamer;
    }

    /**
     * Holds Coat's stream through the real stream tick, then removes the
     * streamer, runs the check and succeeds.
     *
     * @param helper   the gametest helper
     * @param streamer the streaming player
     * @param check    the assertions run once the hold ends
     */
    private static void holdCoat(GameTestHelper helper, ServerPlayer streamer, Runnable check) {
        AbilityDefinition coat = AbilityRegistry.of(helper.getLevel()).getAbility(COAT);
        helper.assertTrue(coat != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(streamer, coat);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.WEIRD), COAT.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        for (int held = 1; held <= COAT_HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
        helper.runAfterDelay(COAT_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            check.run();
            helper.succeed();
        });
    }
}
