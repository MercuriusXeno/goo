package com.mercuriusxeno.goo.network;
import net.minecraft.resources.Identifier;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.LandingHost;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.gametest.FedPlexer;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Zap: a strike on a lever flips it, one on dust powers it
 * for a moment, one on a plain wall disperses into the Signal wave and opens
 * the door behind the wall, and every mob at the strike drops its target and
 * stands without AI until the stun wears off. A strike on a plexer crafts
 * once wherever the pulse must stand, one on a reactor halts it for the
 * pulse, and one on a copper bulb lights it. Each strike arrives the way a
 * beam delivery arrives, an arrival a beam and an arc share.
 * zap-ticks-the-device-and-stuns
 * zap-disperses-into-signal
 * zap-powers-anything-a-signal-runs
 */
@RegistersGameTests
public final class ZapTests {
    private static final String ZAP_TICKS_THE_LEVER = "zap_ticks_the_lever";
    private static final String ZAP_ON_A_WALL_TOGGLES_BEHIND_IT = "zap_on_a_wall_toggles_behind_it";
    private static final String ZAP_STUNS_A_ZOMBIE = "zap_stuns_a_zombie";
    private static final String ZAP_STUN_WEARS_OFF = "zap_stun_wears_off";
    private static final String ZAP_PULSES_DUST = "zap_pulses_dust";
    private static final String ZAP_ON_A_PLEXER_CRAFTS_ONCE = "zap_on_a_plexer_crafts_once";
    private static final String ZAP_INSIDE_THE_PLEXER_CELL_CRAFTS_ONCE = "zap_inside_the_plexer_cell_crafts_once";
    private static final String ZAP_ON_AN_ENCLOSED_PLEXER_CRAFTS_ONCE = "zap_on_an_enclosed_plexer_crafts_once";
    private static final String ZAP_ON_A_REACTOR_TRIGGERS_IT_FOR_ONE_PULSE = "zap_on_a_reactor_triggers_it_for_one_pulse";
    private static final String ZAP_LIGHTS_A_COPPER_BULB = "zap_lights_a_copper_bulb";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, ZAP_TICKS_THE_LEVER, ZapTests::zapTicksTheLever);
        GooTestFunctions.registerTest(registrar, ZAP_ON_A_WALL_TOGGLES_BEHIND_IT, ZapTests::zapOnAWallTogglesBehindIt);
        GooTestFunctions.registerTest(registrar, ZAP_STUNS_A_ZOMBIE, ZapTests::zapStunsAZombie);
        GooTestFunctions.registerTest(registrar, ZAP_STUN_WEARS_OFF, ZapTests::zapStunWearsOff);
        GooTestFunctions.registerTest(registrar, ZAP_PULSES_DUST, ZapTests::zapPulsesDust);
        GooTestFunctions.registerTest(registrar, ZAP_ON_A_PLEXER_CRAFTS_ONCE, ZapTests::zapOnAPlexerCraftsOnce);
        GooTestFunctions.registerTest(registrar, ZAP_INSIDE_THE_PLEXER_CELL_CRAFTS_ONCE,
                ZapTests::zapInsideThePlexerCellCraftsOnce);
        GooTestFunctions.registerTest(registrar, ZAP_ON_AN_ENCLOSED_PLEXER_CRAFTS_ONCE,
                ZapTests::zapOnAnEnclosedPlexerCraftsOnce);
        GooTestFunctions.registerTest(registrar, ZAP_ON_A_REACTOR_TRIGGERS_IT_FOR_ONE_PULSE,
                ZapTests::zapOnAReactorTriggersItForOnePulse);
        GooTestFunctions.registerTest(registrar, ZAP_LIGHTS_A_COPPER_BULB, ZapTests::zapLightsACopperBulb);
    }


    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String ZAP = "goo:pulse_zap";
    private static final String ZAP_REQUIRED = "Ability registry must hold pulse_zap";
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    private static final BlockPos ON_FLOOR = FLOOR.above();
    private static final int SETTLE_TICKS = 1;
    /** Half a block, from a block's center to its face. */
    private static final double BLOCK_HALF = 0.5;
    private static final String LEVER_FLIPPED = "A Zap landing on a lever should flip it on";
    private static final String TARGET_DROPPED = "A stunned zombie should hold no target";
    private static final String AI_OFF = "A stunned zombie should stand without AI";
    private static final String AI_BACK = "A zombie whose stun wore off should have its AI back";
    private static final String DUST_POWERED = "Dust beside a Zap landing should carry power";
    private static final String DUST_UNPOWERED = "Dust should lose its power once the pulse ends";
    private static final String HAD_TARGET = "The zombie should target the player before the Zap";
    /** A stone wall on the floor, and two blocks past it a closed door the wave reaches through the wall. */
    private static final BlockPos WALL = ON_FLOOR;
    private static final BlockPos DOOR = WALL.east(2);
    private static final String DOOR_OPENED = "A Zap on the wall should open the door behind it, toggled once";
    /** Ticks the dust's pulse stands, and a tick past them for it to clear. */
    private static final int PAST_THE_PULSE = 4;
    /** The plexer's hatch side, so its stone ejects into the open cell south of it. */
    private static final Direction HATCH = Direction.SOUTH;
    /** The goo the fed plexer holds: enough for more than one craft, so a second craft would show. */
    private static final int PLEXER_CRAFTS = 4;
    /** Ticks for the plexer's delayed craft to run after the pulse's rising edge, with slack. */
    private static final int PAST_THE_CRAFT = 10;
    private static final String CRAFTED_ONCE = "A Zap on a plexer should eject exactly one stone, ejected ";
    private static final String REACTOR_TRIGGERED = "A reactor should read TRIGGERED on the tick a Zap lands on it";
    private static final String REACTOR_RELEASED = "A reactor should read TRIGGERED false once the Zap's pulse ends";
    private static final String BULB_LIT = "A Zap landing on an unlit copper bulb should light it";

    private ZapTests() {
    }

    /**
     * Zap lands on top of a lever standing on the floor: the lever flips on.
     *
     * @param helper the gametest helper
     */
    public static void zapTicksTheLever(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(ON_FLOOR, Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        landOn(helper, ON_FLOOR);
        helper.assertTrue(helper.getBlockState(ON_FLOOR).getValue(LeverBlock.POWERED), LEVER_FLIPPED);
        helper.succeed();
    }

    /**
     * Zap lands on the west face of a stone wall: the blob disperses into the
     * Signal wave, which passes through the wall and opens the closed door
     * two blocks behind it, once, both halves read as one door.
     *
     * @param helper the gametest helper
     */
    public static void zapOnAWallTogglesBehindIt(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(DOOR.below(), Blocks.STONE);
        helper.setBlock(WALL, Blocks.STONE);
        helper.setBlock(DOOR, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(DOOR.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.PULSE,
                NO_ENTITY, helper.absolutePos(WALL), Direction.WEST, ZAP));
        helper.assertTrue(helper.getBlockState(DOOR).getValue(DoorBlock.OPEN), DOOR_OPENED);
        helper.succeed();
    }

    /**
     * Zap strikes a zombie targeting a player: the zombie drops its target
     * and stands without AI.
     *
     * @param helper the gametest helper
     */
    public static void zapStunsAZombie(GameTestHelper helper) {
        Mob zombie = targetingZombie(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(zombie.getTarget() != null, HAD_TARGET);
            strike(helper, zombie);
            helper.assertTrue(zombie.getTarget() == null, TARGET_DROPPED);
            helper.assertTrue(zombie.isNoAi(), AI_OFF);
            helper.succeed();
        });
    }

    /**
     * A zombie Zap stunned has its AI back once the stun wears off, polled so
     * the wait follows the stun's ticks however they are tuned.
     *
     * @param helper the gametest helper
     */
    public static void zapStunWearsOff(GameTestHelper helper) {
        Mob zombie = targetingZombie(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, zombie);
            helper.assertTrue(zombie.isNoAi(), AI_OFF);
            helper.succeedWhen(() -> helper.assertFalse(zombie.isNoAi(), AI_BACK));
        });
    }

    /**
     * Zap lands on top of dust on the floor: the dust carries power while
     * the pulse stands and none once it ends.
     *
     * @param helper the gametest helper
     */
    public static void zapPulsesDust(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(ON_FLOOR, Blocks.REDSTONE_WIRE);
        landOn(helper, ON_FLOOR);
        helper.assertTrue(helper.getBlockState(ON_FLOOR).getValue(RedStoneWireBlock.POWER) > 0, DUST_POWERED);
        helper.runAfterDelay(PAST_THE_PULSE, () -> {
            helper.assertTrue(helper.getBlockState(ON_FLOOR).getValue(RedStoneWireBlock.POWER) == 0, DUST_UNPOWERED);
            helper.succeed();
        });
    }

    /**
     * Zap lands in the open cell west of a fed plexer: the plexer takes one
     * pulse and ejects one stone.
     *
     * @param helper the gametest helper
     */
    public static void zapOnAPlexerCraftsOnce(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        FedPlexer.place(helper, ON_FLOOR, HATCH, PLEXER_CRAFTS);
        landOn(helper, ON_FLOOR, Direction.WEST);
        assertCraftedOnce(helper);
    }

    /**
     * Zap's program runs on a landing whose cell is the fed plexer's own
     * cell, a cell no placement rule hands a plexer today but a waterlogged
     * receiver takes: the pulse stands in an open cell beside the plexer,
     * which ejects one stone.
     *
     * @param helper the gametest helper
     */
    public static void zapInsideThePlexerCellCraftsOnce(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        FedPlexer.place(helper, ON_FLOOR, HATCH, PLEXER_CRAFTS);
        BlockPos cell = helper.absolutePos(ON_FLOOR);
        LandingHost host = new LandingHost(helper.getLevel(), cell, Direction.UP, false, GooTypes.PULSE, ZAP,
                Vec3.atCenterOf(cell), 0, null);
        ProgramBehavior.forHost(zap(helper).behaviors(), HostKind.LANDING).tick(host);
        assertCraftedOnce(helper);
    }

    /**
     * Zap strikes the top of a fed plexer between its canisters, the plexer
     * walled in stone on every other side but its hatch: the blob lands in
     * the hatch's open cell, the plexer takes the pulse there, and it
     * ejects one stone.
     *
     * @param helper the gametest helper
     */
    public static void zapOnAnEnclosedPlexerCraftsOnce(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(FLOOR.relative(HATCH), Blocks.STONE);
        FedPlexer.place(helper, ON_FLOOR, HATCH, PLEXER_CRAFTS);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side != HATCH) {
                helper.setBlock(ON_FLOOR.relative(side), Blocks.STONE);
            }
        }
        BlockPos plexer = helper.absolutePos(ON_FLOOR);
        Vec3 top = Vec3.atCenterOf(plexer).add(0, BLOCK_HALF, 0);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.PULSE,
                NO_ENTITY, plexer, Direction.UP, ZAP, null, top));
        assertCraftedOnce(helper);
    }

    /**
     * Zap lands on top of a reactor: the reactor reads TRIGGERED while the
     * pulse stands, which halts it, and false once the pulse ends.
     *
     * @param helper the gametest helper
     */
    public static void zapOnAReactorTriggersItForOnePulse(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(ON_FLOOR, GooBlocks.REACTOR.get());
        landOn(helper, ON_FLOOR);
        helper.assertTrue(helper.getBlockState(ON_FLOOR).getValue(ReactorBlock.TRIGGERED), REACTOR_TRIGGERED);
        helper.runAfterDelay(PAST_THE_PULSE, () -> {
            helper.assertFalse(helper.getBlockState(ON_FLOOR).getValue(ReactorBlock.TRIGGERED), REACTOR_RELEASED);
            helper.succeed();
        });
    }

    /**
     * Zap lands on top of an unlit copper bulb: the bulb lights.
     *
     * @param helper the gametest helper
     */
    public static void zapLightsACopperBulb(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(ON_FLOOR, Blocks.COPPER_BULB);
        landOn(helper, ON_FLOOR);
        helper.assertTrue(helper.getBlockState(ON_FLOOR).getValue(CopperBulbBlock.LIT), BULB_LIT);
        helper.succeed();
    }

    private static void assertCraftedOnce(GameTestHelper helper) {
        helper.runAfterDelay(PAST_THE_CRAFT, () -> {
            int ejected = FedPlexer.ejected(helper);
            helper.assertTrue(ejected == 1, CRAFTED_ONCE + ejected);
            helper.succeed();
        });
    }

    private static Mob targetingZombie(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(ON_FLOOR.east(2).getCenter()));
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ON_FLOOR);
        zombie.setTarget(player);
        return zombie;
    }


    private static AbilityDefinition zap(GameTestHelper helper) {
        AbilityDefinition zap = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ZAP));
        helper.assertTrue(zap != null, ZAP_REQUIRED);
        return zap;
    }

    private static void landOn(GameTestHelper helper, BlockPos block) {
        landOn(helper, block, Direction.UP);
    }

    private static void landOn(GameTestHelper helper, BlockPos block, Direction face) {
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.PULSE,
                NO_ENTITY, helper.absolutePos(block), face, ZAP));
    }

    private static void strike(GameTestHelper helper, Mob mob) {
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.PULSE,
                mob.getId(), mob.blockPosition(), Direction.UP, ZAP));
    }
}
