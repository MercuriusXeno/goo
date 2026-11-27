package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.FreezeBlocksStep;
import com.mercuriusxeno.goo.ability.program.NovaStep;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.entity.RollingGoo;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Gametests for the frost abilities: each lands a frost ability the way the
 * glove would and reads the frozen gauges and the world it leaves.
 * Decisions nova-ring-grows-with-the-hold,
 * cold-streams-wind-lines-and-snowflakes, orb-carries-a-swirling-nova.
 */
@RegistersGameTests
public final class FrostAbilityTests {
    private static final String NOVA_HELD_REACHES_BOTH = "nova_held_reaches_both_zombies";
    private static final String NOVA_FREEZES_WATER_AND_LAVA = "nova_freezes_water_and_lava_in_its_ring";
    private static final String COLD_BREAKS_GRASS = "cold_breaks_the_grass_in_its_cone";
    private static final String COLD_FREEZES_WATER_AND_LAVA = "cold_freezes_water_and_lava_in_its_cone";
    private static final String COLD_COOLS_MOLTEN_IRON_AND_LAVA = "cold_cools_molten_iron_and_lava_in_its_cone";
    private static final String FREEZE_COOLS_ONLY_THE_MOLTEN_SOURCE = "freeze_cools_only_the_molten_source";
    private static final String ORB_FREEZES_PATH = "orb_freezes_path_and_pool";
    private static final String ORB_ENDS_ON_A_MOB = "orb_ends_on_a_mob";
    private static final String ORB_ENDS_AT_ITS_RANGE = "orb_ends_at_its_range";
    private static final String FROZEN_WITCH_DRINKS_NO_POTION = "frozen_witch_drinks_no_potion";
    private static final String FROZEN_WITCH_FALLS_AND_THAWS = "frozen_witch_falls_and_thaws";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, NOVA_HELD_REACHES_BOTH, FrostAbilityTests::novaHeldReachesBothZombies);
        GooTestFunctions.registerTest(registrar, NOVA_FREEZES_WATER_AND_LAVA,
                FrostAbilityTests::novaFreezesWaterAndLavaInItsRing);
        GooTestFunctions.registerTest(registrar, COLD_BREAKS_GRASS, FrostAbilityTests::coldBreaksTheGrassInItsCone);
        GooTestFunctions.registerTest(registrar, COLD_FREEZES_WATER_AND_LAVA,
                FrostAbilityTests::coldFreezesWaterAndLavaInItsCone);
        GooTestFunctions.registerTest(registrar, COLD_COOLS_MOLTEN_IRON_AND_LAVA,
                FrostAbilityTests::coldCoolsMoltenIronAndLavaInItsCone);
        GooTestFunctions.registerTest(registrar, FREEZE_COOLS_ONLY_THE_MOLTEN_SOURCE,
                FrostAbilityTests::freezeCoolsOnlyTheMoltenSource);
        GooTestFunctions.registerTest(registrar, ORB_FREEZES_PATH, FrostAbilityTests::orbFreezesPathAndPool);
        GooTestFunctions.registerTest(registrar, ORB_ENDS_ON_A_MOB, FrostAbilityTests::orbEndsOnAMob);
        GooTestFunctions.registerTest(registrar, ORB_ENDS_AT_ITS_RANGE, FrostAbilityTests::orbEndsAtItsRange);
        GooTestFunctions.registerTest(registrar, FROZEN_WITCH_DRINKS_NO_POTION, FrostAbilityTests::frozenWitchDrinksNoPotion);
        GooTestFunctions.registerTest(registrar, FROZEN_WITCH_FALLS_AND_THAWS, FrostAbilityTests::frozenWitchFallsAndThaws);
    }


    private static final BlockPos CASTER_POS = new BlockPos(1, 1, 1);
    /** About one and a half blocks from the caster, close beside it. */
    private static final BlockPos NEAR_POS = new BlockPos(2, 1, 2);
    /** About four blocks from the caster, well inside a full Nova's reach. */
    private static final BlockPos FAR_POS = new BlockPos(4, 1, 4);
    private static final Identifier FROST_NOVA = Identifier.parse("goo:frost_nova");
    private static final int SETTLE_TICKS = 1;
    /** The part of Nova's full charge the ring test holds. */
    private static final int THIRD = 3;
    /** Still water and lava in the floor about two and a half blocks from the caster, inside a third-held ring. */
    private static final BlockPos NOVA_WATER_POS = new BlockPos(3, 0, 1);
    private static final BlockPos NOVA_LAVA_POS = new BlockPos(1, 0, 3);
    private static final String SHOULD_ICE_IN_THE_RING = "Nova's ring should freeze the water it reaches to ice";
    private static final String SHOULD_OBSIDIAN_IN_THE_RING = "Nova's ring should freeze the lava it reaches to obsidian";
    /** Still molten iron in a stone basin inside a third-held ring, walled from the water's spread. */
    private static final BlockPos NOVA_MOLTEN_IRON_POS = new BlockPos(2, 0, 2);
    private static final String SHOULD_IRON_CORE_IN_THE_RING =
            "Nova's ring should cool the molten iron it reaches to iron core";
    private static final Identifier FROST_COLD = Identifier.parse("goo:frost_cold");
    private static final BlockPos STREAMER_POS = new BlockPos(0, 1, 2);
    /** Grass three blocks east of the streamer, inside Cold's cone below the look. */
    private static final BlockPos GRASS_POS = new BlockPos(3, 1, 2);
    private static final float FACING_EAST = -90f;
    /** Pitched down short of the grass, so it stands in the cone below the look. */
    private static final float ABOVE_THE_GRASS = 14f;
    private static final int COLD_HOLD_TICKS = 10;
    /** Pitched down at the floor, so the cone's floor cells four to six blocks out stand near its axis. */
    private static final float AT_THE_FLOOR = 25f;
    /** Still water and lava in the floor inside Cold's cone, a block apart so neither meets the other. */
    private static final BlockPos COLD_WATER_POS = new BlockPos(4, 0, 2);
    private static final BlockPos COLD_LAVA_POS = new BlockPos(6, 0, 2);
    private static final String SHOULD_ICE_IN_THE_CONE = "Cold's cone should freeze the water it reaches to ice";
    private static final String SHOULD_OBSIDIAN_IN_THE_CONE = "Cold's cone should freeze the lava it reaches to obsidian";
    /**
     * Still molten iron on Cold's axis, a block short of the lava so neither meets the other; no basin walls it, since
     * a wall standing in the cone would end Cold's aim short of the lava.
     */
    private static final BlockPos COLD_MOLTEN_IRON_POS = new BlockPos(4, 0, 2);
    private static final String SHOULD_IRON_CORE_IN_THE_CONE =
            "Cold's cone should cool the molten iron it reaches to iron core";
    /** Still molten iron in a stone basin beside the Orb's roll, inside its freeze sphere, walled from the pools. */
    private static final BlockPos ORB_MOLTEN_IRON_POS = new BlockPos(1, 0, 4);
    private static final String SHOULD_IRON_CORE_BESIDE_THE_ROLL =
            "The Orb's freeze sphere should cool the molten iron beside its roll to iron core";
    /** A molten iron source, flowing molten iron and flowing lava in a row, two blocks apart. */
    private static final BlockPos FREEZE_SOURCE_POS = new BlockPos(1, 0, 3);
    private static final BlockPos FREEZE_FLOWING_IRON_POS = new BlockPos(3, 0, 3);
    private static final BlockPos FREEZE_FLOWING_LAVA_POS = new BlockPos(5, 0, 3);
    /** A flowing cell's level, one step down from its source. */
    private static final int FLOWING_LEVEL = 2;
    /** Reaches the three cells, each two blocks from the middle one's center. */
    private static final double FREEZE_REACH = 2.5;
    private static final String SHOULD_IRON_CORE_AT_THE_SOURCE = "A freeze should cool a molten iron source to iron core";
    private static final String SHOULD_FLOW_ON = "A freeze should leave flowing %s liquid, stands %s";
    private static final Identifier FROST_ORB = Identifier.parse("goo:frost_orb");
    /** Two still water blocks in the floor under the Orb's path. */
    private static final BlockPos POOL_NEAR = new BlockPos(2, 0, 2);
    private static final BlockPos POOL_FAR = new BlockPos(3, 0, 2);
    /**
     * Still water in the floor's west corner beside the throw's start: about four blocks from the
     * roll, past the swirl's reach and inside the freeze sphere's, and over six from where the Orb
     * ends by the east wall.
     */
    private static final BlockPos ORB_WIDE_WATER_POS = new BlockPos(0, 0, 6);
    /** A zombie off the Orb's path near the bay's east wall, inside the end nova's reach. */
    private static final BlockPos ORB_BYSTANDER_POS = new BlockPos(5, 1, 4);
    /** A zombie standing in the Orb's path, which ends it. */
    private static final BlockPos ORB_BLOCKER_POS = new BlockPos(3, 1, 2);
    /** Enough frost for the Orb's cost and some over. */
    private static final int ORB_GOO = 4;
    /** How far ahead along the look a test aims a free throw. */
    private static final double SELF_FREE_AIM_REACH = 4;
    /** The bay's far corner, six blocks out on each axis. */
    private static final BlockPos BAY_FAR_CORNER = new BlockPos(6, 6, 6);
    /** Well past the ticks the Orb takes to roll the bay's six blocks to its end. */
    private static final int ORB_ENDED_TICKS = 40;
    private static final String SHOULD_ICE_THE_POOL = "The Orb should freeze the pool under its path to ice";
    private static final String SHOULD_ICE_WIDE = "The Orb's freeze sphere should freeze the water four blocks beside its roll";
    private static final String SHOULD_FREEZE_BYSTANDER = "The Orb's end nova should freeze the zombie by the wall, stands %s";
    private static final String SHOULD_FREEZE_BLOCKER = "The zombie the Orb struck should be frozen, stands %s";
    private static final String SHOULD_END = "The Orb should be gone once it ends";
    /** Blocks above the bay the range test rolls the Orb through, clear of every bay. */
    private static final int OPEN_SKY = 24;
    /** The share of the Orb's roll, its range over its pace read live, at which it should still be rolling. */
    private static final double STILL_ROLLING_SHARE = 0.75;
    /** The share of the Orb's roll by which it should be gone, well past its end. */
    private static final double RANGE_SPENT_SHARE = 1.25;
    /** Blocks past the Orb's range the held chunks reach, so every chunk the roll crosses is held loaded. */
    private static final int ROLL_REACH_MARGIN = 4;
    private static final String SHOULD_STILL_ROLL = "The Orb should still be rolling short of its range";
    private static final String SHOULD_END_AT_RANGE_FROM =
            "The Orb should end once it has rolled its range; it stands %s from its start, entity ticking %s";
    private static final String ABILITY_REQUIRED = "Ability registry must hold frost_nova";
    private static final String SHOULD_FREEZE = "A full Nova should freeze the %s zombie, stands %s";
    private static final String SHOULD_HURT = "Nova's ring should hurt the %s zombie it crosses";
    private static final BlockPos WITCH_POS = new BlockPos(3, 1, 3);
    /** The most full Novas released at a witch to fill her gauge, past what any tuning needs. */
    private static final int MOST_NOVAS_TO_ENCASE_A_WITCH = 10;
    private static final String SHOULD_ENCASE_THE_WITCH = "Full Novas should fill a witch's frozen gauge";
    private static final String SHOULD_HOLD_NO_POTION = "A frozen witch should hold no potion, holds %s on hold tick %s";
    private static final String SHOULD_NOT_DRINK = "A frozen witch should not be drinking, on hold tick %s";
    /** Three blocks over the bay's floor, so the witch is still falling when the Novas freeze her. */
    private static final BlockPos WITCH_DROP_POS = new BlockPos(3, 4, 3);
    /** Ticks into the hold by which a three-block fall has landed. */
    private static final int LANDED_TICKS = 40;
    /** Ticks past the hold by which the thaw has taken the gauge below full. */
    private static final int WITCH_THAW_CHECK_TICKS = 3;
    private static final String SHOULD_FALL_WHILE_FROZEN =
            "A frozen witch should fall to the floor while held, stands %s blocks below where she froze, on ground %s";
    private static final String SHOULD_THAW_THE_WITCH = "A frozen witch should thaw below full once her hold runs out";
    private static final String SHOULD_GIVE_THE_WITCH_AI_BACK = "A thawed witch should have her AI back";

    private FrostAbilityTests() {
    }

    /**
     * A full hold of Nova released beside two zombies reaches both and
     * freezes each.
     *
     * @param helper the gametest helper
     */
    public static void novaHeldReachesBothZombies(GameTestHelper helper) {
        Mob near = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, NEAR_POS);
        Mob far = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, FAR_POS);
        ServerPlayer caster = caster(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            release(caster, fullHoldTicks(helper));
            helper.assertTrue(near.getHealth() < near.getMaxHealth(), String.format(SHOULD_HURT, "near"));
            helper.assertTrue(far.getHealth() < far.getMaxHealth(), String.format(SHOULD_HURT, "far"));
            float nearGauge = near.getData(GooAttachments.FROZEN).gauge();
            float farGauge = far.getData(GooAttachments.FROZEN).gauge();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(nearGauge > 0, String.format(SHOULD_FREEZE, "near", nearGauge));
            helper.assertTrue(farGauge > 0, String.format(SHOULD_FREEZE, "far", farGauge));
            helper.succeed();
        });
    }

    /**
     * Nova held a third of its charge freezes the still water in its ring to
     * ice and the still lava to obsidian (decision nova-ring-and-cold-cone-freeze-water-and-lava).
     *
     * @param helper the gametest helper
     */
    public static void novaFreezesWaterAndLavaInItsRing(GameTestHelper helper) {
        helper.setBlock(NOVA_WATER_POS, Blocks.WATER);
        helper.setBlock(NOVA_LAVA_POS, Blocks.LAVA);
        basin(helper, NOVA_MOLTEN_IRON_POS, GooBlocks.MOLTEN_IRON.get());
        ServerPlayer caster = caster(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            release(caster, fullHoldTicks(helper) / THIRD);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(helper.getBlockState(NOVA_WATER_POS).is(Blocks.ICE), SHOULD_ICE_IN_THE_RING);
            helper.assertTrue(helper.getBlockState(NOVA_LAVA_POS).is(Blocks.OBSIDIAN), SHOULD_OBSIDIAN_IN_THE_RING);
            helper.assertTrue(helper.getBlockState(NOVA_MOLTEN_IRON_POS).is(GooBlocks.IRON_CORE.get()),
                    SHOULD_IRON_CORE_IN_THE_RING);
            helper.succeed();
        });
    }

    /**
     * Cold streamed along a line of grass breaks the grass in its cone.
     *
     * @param helper the gametest helper
     */
    public static void coldBreaksTheGrassInItsCone(GameTestHelper helper) {
        helper.setBlock(GRASS_POS.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(GRASS_POS, Blocks.SHORT_GRASS);
        ServerPlayer streamer = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STREAMER_POS));
        streamer.setPos(stand.x, stand.y, stand.z);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(ABOVE_THE_GRASS);
        AbilityDefinition cold = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_COLD);
        helper.assertTrue(cold != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(streamer, cold);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.FROST), FROST_COLD.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        for (int held = 1; held <= COLD_HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
        helper.runAfterDelay(COLD_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertBlockNotPresent(Blocks.SHORT_GRASS, GRASS_POS);
            helper.succeed();
        });
    }

    /**
     * Cold held ten ticks at the floor freezes the still water in its cone to
     * ice and the still lava to obsidian (decision nova-ring-and-cold-cone-freeze-water-and-lava).
     *
     * @param helper the gametest helper
     */
    public static void coldFreezesWaterAndLavaInItsCone(GameTestHelper helper) {
        helper.setBlock(COLD_WATER_POS, Blocks.WATER);
        helper.setBlock(COLD_LAVA_POS, Blocks.LAVA);
        ServerPlayer streamer = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STREAMER_POS));
        streamer.setPos(stand.x, stand.y, stand.z);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(AT_THE_FLOOR);
        AbilityDefinition cold = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_COLD);
        helper.assertTrue(cold != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(streamer, cold);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.FROST), FROST_COLD.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        for (int held = 1; held <= COLD_HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
        helper.runAfterDelay(COLD_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertTrue(helper.getBlockState(COLD_WATER_POS).is(Blocks.ICE), SHOULD_ICE_IN_THE_CONE);
            helper.assertTrue(helper.getBlockState(COLD_LAVA_POS).is(Blocks.OBSIDIAN), SHOULD_OBSIDIAN_IN_THE_CONE);
            helper.succeed();
        });
    }

    /**
     * Cold held ten ticks at the floor cools the molten iron source in its cone
     * to iron core and the lava source to obsidian, each by the pair the molten
     * groups hold at its position (decisions frost-cools-molten-blocks-into-molten-cooled-blocks,
     * every-frost-freeze-turns-a-molten-iron-source-to-iron-core).
     *
     * @param helper the gametest helper
     */
    public static void coldCoolsMoltenIronAndLavaInItsCone(GameTestHelper helper) {
        helper.setBlock(COLD_MOLTEN_IRON_POS, GooBlocks.MOLTEN_IRON.get());
        helper.setBlock(COLD_LAVA_POS, Blocks.LAVA);
        ServerPlayer streamer = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STREAMER_POS));
        streamer.setPos(stand.x, stand.y, stand.z);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(AT_THE_FLOOR);
        AbilityDefinition cold = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_COLD);
        helper.assertTrue(cold != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(streamer, cold);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.FROST), FROST_COLD.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        for (int held = 1; held <= COLD_HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
        helper.runAfterDelay(COLD_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertTrue(helper.getBlockState(COLD_MOLTEN_IRON_POS).is(GooBlocks.IRON_CORE.get()),
                    SHOULD_IRON_CORE_IN_THE_CONE);
            helper.assertTrue(helper.getBlockState(COLD_LAVA_POS).is(Blocks.OBSIDIAN), SHOULD_OBSIDIAN_IN_THE_CONE);
            helper.succeed();
        });
    }

    /**
     * A freeze over a molten iron source, a flowing molten iron cell and a
     * flowing lava cell cools the source to iron core and leaves both flowing
     * cells liquid: only a source cools (decision every-frost-freeze-turns-a-molten-iron-source-to-iron-core).
     *
     * @param helper the gametest helper
     */
    public static void freezeCoolsOnlyTheMoltenSource(GameTestHelper helper) {
        BlockState flowingIron = GooBlocks.MOLTEN_IRON.get().defaultBlockState()
                .setValue(LiquidBlock.LEVEL, FLOWING_LEVEL);
        BlockState flowingLava = Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, FLOWING_LEVEL);
        helper.setBlock(FREEZE_SOURCE_POS, GooBlocks.MOLTEN_IRON.get());
        helper.setBlock(FREEZE_FLOWING_IRON_POS, flowingIron);
        helper.setBlock(FREEZE_FLOWING_LAVA_POS, flowingLava);
        FreezeBlocksStep.freezeWithin(helper.getLevel(), Vec3.atCenterOf(helper.absolutePos(FREEZE_FLOWING_IRON_POS)),
                FREEZE_REACH, false);
        BlockState ironAfter = helper.getBlockState(FREEZE_FLOWING_IRON_POS);
        BlockState lavaAfter = helper.getBlockState(FREEZE_FLOWING_LAVA_POS);
        helper.assertTrue(helper.getBlockState(FREEZE_SOURCE_POS).is(GooBlocks.IRON_CORE.get()),
                SHOULD_IRON_CORE_AT_THE_SOURCE);
        helper.assertTrue(ironAfter.equals(flowingIron), String.format(SHOULD_FLOW_ON, "molten iron", ironAfter));
        helper.assertTrue(lavaAfter.equals(flowingLava), String.format(SHOULD_FLOW_ON, "lava", lavaAfter));
        helper.succeed();
    }

    /**
     * The Orb thrown along the bay over a pool and past a zombie freezes the
     * pool to ice and the zombie as it rolls by, and its freeze sphere freezes
     * the water four blocks beside its roll (decision orb-freeze-sphere-matches-its-nova).
     *
     * @param helper the gametest helper
     */
    public static void orbFreezesPathAndPool(GameTestHelper helper) {
        helper.setBlock(POOL_NEAR, Blocks.WATER);
        helper.setBlock(POOL_FAR, Blocks.WATER);
        helper.setBlock(ORB_WIDE_WATER_POS, Blocks.WATER);
        basin(helper, ORB_MOLTEN_IRON_POS, GooBlocks.MOLTEN_IRON.get());
        helper.setBlock(ORB_BYSTANDER_POS.below(), Blocks.STONE);
        Mob bystander = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, ORB_BYSTANDER_POS);
        ServerPlayer thrower = orbThrower(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> throwOrbEast(helper, thrower));
        helper.runAfterDelay(ORB_ENDED_TICKS, () -> {
            float gauge = bystander.getData(GooAttachments.FROZEN).gauge();
            helper.getLevel().getServer().getPlayerList().remove(thrower);
            helper.assertTrue(helper.getBlockState(POOL_NEAR).is(Blocks.ICE)
                    && helper.getBlockState(POOL_FAR).is(Blocks.ICE), SHOULD_ICE_THE_POOL);
            helper.assertTrue(helper.getBlockState(ORB_WIDE_WATER_POS).is(Blocks.ICE), SHOULD_ICE_WIDE);
            helper.assertTrue(helper.getBlockState(ORB_MOLTEN_IRON_POS).is(GooBlocks.IRON_CORE.get()),
                    SHOULD_IRON_CORE_BESIDE_THE_ROLL);
            helper.assertTrue(gauge > 0, String.format(SHOULD_FREEZE_BYSTANDER, gauge));
            helper.assertTrue(noRollingGoo(helper), SHOULD_END);
            helper.succeed();
        });
    }

    /**
     * The Orb rolled at a zombie in its path ends on it, freezing it.
     *
     * @param helper the gametest helper
     */
    public static void orbEndsOnAMob(GameTestHelper helper) {
        helper.setBlock(ORB_BLOCKER_POS.below(), Blocks.STONE);
        Mob blocker = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, ORB_BLOCKER_POS);
        ServerPlayer thrower = orbThrower(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> throwOrbEast(helper, thrower));
        helper.runAfterDelay(ORB_ENDED_TICKS, () -> {
            float gauge = blocker.getData(GooAttachments.FROZEN).gauge();
            helper.getLevel().getServer().getPlayerList().remove(thrower);
            helper.assertTrue(gauge > 0, String.format(SHOULD_FREEZE_BLOCKER, gauge));
            helper.assertTrue(noRollingGoo(helper), SHOULD_END);
            helper.succeed();
        });
    }

    /**
     * The Orb rolled through open sky rolls its range and no further: still
     * rolling three quarters of the way, gone well past the end, the pace and
     * range read live from the Orb.
     *
     * @param helper the gametest helper
     */
    public static void orbEndsAtItsRange(GameTestHelper helper) {
        ServerPlayer thrower = orbThrower(helper);
        AbilityDefinition orb = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_ORB);
        double rollTicks = orb.delivery().range() / orb.delivery().blocksPerTick();
        int stillRolling = SETTLE_TICKS + (int) (rollTicks * STILL_ROLLING_SHARE);
        int rangeSpent = SETTLE_TICKS + (int) Math.ceil(rollTicks * RANGE_SPENT_SHARE);
        int reach = (int) Math.ceil(orb.delivery().range()) + ROLL_REACH_MARGIN;
        Vec3 sky = Vec3.atCenterOf(helper.absolutePos(STREAMER_POS.above(OPEN_SKY)));
        // the roll leaves the bay's chunks, and an entity in a chunk that is not ticking stops short of its range
        holdRollLoaded(helper, sky, reach, true);
        // the orb this test rolled, so a neighboring test's orb rolling past never stands in for it
        AtomicReference<RollingGoo> rolled = new AtomicReference<>();
        helper.runAfterDelay(SETTLE_TICKS, () -> rolled.set(RollingGoo.roll(helper.getLevel(), thrower, orb, sky,
                new Vec3(1, 0, 0))));
        helper.runAfterDelay(stillRolling, () -> helper.assertFalse(rolled.get().isRemoved(),
                SHOULD_STILL_ROLL));
        helper.runAfterDelay(rangeSpent, () -> {
            helper.getLevel().getServer().getPlayerList().remove(thrower);
            holdRollLoaded(helper, sky, reach, false);
            RollingGoo goo = rolled.get();
            helper.assertTrue(goo.isRemoved(), String.format(SHOULD_END_AT_RANGE_FROM, goo.position().subtract(sky),
                    helper.getLevel().isPositionEntityTicking(goo.blockPosition())));
            helper.succeed();
        });
    }

    /**
     * Holds loaded, or lets go of, every chunk the range test's Orb rolls
     * through east of its start.
     *
     * @param helper the gametest helper
     * @param start  where the Orb starts rolling
     * @param reach  how many blocks east of the start the chunks are held
     * @param loaded true to hold the chunks loaded, false to let them unload
     */
    private static void holdRollLoaded(GameTestHelper helper, Vec3 start, int reach, boolean loaded) {
        ChunkPos from = ChunkPos.containing(BlockPos.containing(start));
        ChunkPos to = ChunkPos.containing(BlockPos.containing(start.add(reach, 0, 0)));
        for (int x = from.x(); x <= to.x(); x++) {
            helper.getLevel().setChunkForced(x, from.z(), loaded);
        }
    }

    private static ServerPlayer orbThrower(GameTestHelper helper) {
        ServerPlayer thrower = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        thrower.getInventory().add(GooStacks.createForOutput(GooTypes.FROST, ORB_GOO * GooStacks.THOUSAND));
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STREAMER_POS));
        thrower.setPos(stand.x, stand.y, stand.z);
        thrower.setYRot(FACING_EAST);
        thrower.setXRot(0f);
        AbilityDefinition orb = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_ORB);
        helper.assertTrue(orb != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(thrower, orb);
        return thrower;
    }

    private static void throwOrbEast(GameTestHelper helper, ServerPlayer thrower) {
        Vec3 ahead = thrower.getEyePosition().add(thrower.getLookAngle().scale(SELF_FREE_AIM_REACH));
        GooThrowHandler.execute(thrower, new GooThrowPayload(GooTypes.id(GooTypes.FROST), -1,
                BlockPos.containing(ahead), Direction.UP.ordinal(), false, FROST_ORB.toString(),
                thrower.getEyePosition(), ahead));
    }

    private static boolean noRollingGoo(GameTestHelper helper) {
        AABB bay = new AABB(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)),
                Vec3.atLowerCornerOf(helper.absolutePos(BAY_FAR_CORNER)));
        return helper.getLevel().getEntitiesOfClass(RollingGoo.class, bay.inflate(1)).isEmpty();
    }

    /**
     * A witch frozen full by Nova drinks no potion while encased: on every
     * tick of the hold her main hand holds no potion and she is not drinking
     * (decision diagnose-and-correct-held-mobs-still-acting).
     *
     * @param helper the gametest helper
     */
    public static void frozenWitchDrinksNoPotion(GameTestHelper helper) {
        Witch witch = helper.spawn(EntityTypes.WITCH, WITCH_POS);
        ServerPlayer caster = caster(helper);
        int hold = novaHoldTicks(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> encaseWithNovas(helper, caster, witch));
        for (int held = 1; held < hold; held++) {
            int holdTick = held;
            helper.runAfterDelay(SETTLE_TICKS + held, () -> {
                ItemStack hand = witch.getMainHandItem();
                helper.assertFalse(hand.is(Items.POTION), String.format(SHOULD_HOLD_NO_POTION, hand, holdTick));
                helper.assertFalse(witch.isDrinkingPotion(), String.format(SHOULD_NOT_DRINK, holdTick));
            });
        }
        helper.runAfterDelay(SETTLE_TICKS + hold, helper::succeed);
    }

    /**
     * A witch frozen full by Nova in mid air still falls to the floor while
     * encased, and once the hold runs out she thaws below full with her AI
     * back: the gate on her potion turn leaves the rest of her tick running
     * (decision diagnose-and-correct-held-mobs-still-acting).
     *
     * @param helper the gametest helper
     */
    public static void frozenWitchFallsAndThaws(GameTestHelper helper) {
        Witch witch = helper.spawn(EntityTypes.WITCH, WITCH_DROP_POS);
        ServerPlayer caster = caster(helper);
        double[] frozeAt = new double[1];
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            encaseWithNovas(helper, caster, witch);
            frozeAt[0] = witch.getY();
        });
        helper.runAfterDelay(SETTLE_TICKS + LANDED_TICKS, () -> {
            double fallen = frozeAt[0] - witch.getY();
            helper.assertTrue(witch.getData(GooAttachments.FROZEN).full() && witch.onGround() && fallen > 1,
                    String.format(SHOULD_FALL_WHILE_FROZEN, fallen, witch.onGround()));
        });
        helper.runAfterDelay(SETTLE_TICKS + novaHoldTicks(helper) + WITCH_THAW_CHECK_TICKS, () -> {
            helper.assertFalse(witch.getData(GooAttachments.FROZEN).full(), SHOULD_THAW_THE_WITCH);
            helper.assertFalse(witch.isNoAi(), SHOULD_GIVE_THE_WITCH_AI_BACK);
            helper.succeed();
        });
    }

    /**
     * Fills a witch's frozen gauge with full Novas the caster releases
     * through the charge payload, one after another until her gauge is full,
     * then takes the caster out of the level.
     *
     * @param helper the gametest helper
     * @param caster the Nova's caster
     * @param witch  the witch the Novas freeze
     */
    private static void encaseWithNovas(GameTestHelper helper, ServerPlayer caster, Witch witch) {
        int fullHold = fullHoldTicks(helper);
        // the goo for every Nova the loop may release, at whatever Nova is tuned to cost
        int novaCost = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_NOVA).cost().in(GooValueTable.EMPTY);
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.FROST, MOST_NOVAS_TO_ENCASE_A_WITCH * novaCost));
        for (int released = 0; released < MOST_NOVAS_TO_ENCASE_A_WITCH
                && !witch.getData(GooAttachments.FROZEN).full(); released++) {
            release(caster, fullHold);
        }
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.assertTrue(witch.getData(GooAttachments.FROZEN).full(), SHOULD_ENCASE_THE_WITCH);
    }

    /**
     * The ticks of a full Nova charge, read live from Nova's delivery.
     *
     * @param helper the gametest helper
     * @return the charge's most ticks
     */
    private static int fullHoldTicks(GameTestHelper helper) {
        return nova(helper).delivery().charge().maxTicks();
    }

    /**
     * The ticks a full freeze holds, read live from Nova's ring. The Novas' damage leaves the witch
     * hurt, and a hurt witch picks a healing potion on about one tick in twenty, so a hold this long
     * sees her drink unless the hold stops her.
     *
     * @param helper the gametest helper
     * @return the hold's ticks
     */
    private static int novaHoldTicks(GameTestHelper helper) {
        return nova(helper).behaviors().stream().filter(NovaStep.class::isInstance).map(NovaStep.class::cast)
                .findFirst().orElseThrow().curve().holdTicks();
    }

    private static AbilityDefinition nova(GameTestHelper helper) {
        AbilityDefinition nova = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_NOVA);
        helper.assertTrue(nova != null, ABILITY_REQUIRED);
        return nova;
    }

    private static void release(ServerPlayer caster, int heldTicks) {
        GooThrowHandler.releaseCharge(caster,
                new GooChargePayload(GooTypes.id(GooTypes.FROST), FROST_NOVA.toString(), heldTicks));
    }

    /**
     * Sets a still fluid in a one-block stone basin, so it stays where it was
     * rather than flowing off the bay's open floor.
     *
     * @param helper the gametest helper
     * @param pos    where the fluid stands
     * @param fluid  the fluid's block
     */
    private static void basin(GameTestHelper helper, BlockPos pos, Block fluid) {
        helper.setBlock(pos.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(pos.relative(side), Blocks.STONE);
        }
        helper.setBlock(pos, fluid);
    }

    private static ServerPlayer caster(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        AbilityDefinition nova = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_NOVA);
        helper.assertTrue(nova != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(player, nova);
        return player;
    }
}
