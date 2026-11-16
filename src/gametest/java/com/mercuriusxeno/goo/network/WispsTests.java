package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.entity.DriftingWisp;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Glow's Wisps through the real landing its stream's drips take, in a
 * sealed dark stone room: a drip striking the floor lays a one-layer Wisps
 * cell shining at light 1, a full cell shines at 15, and a zombie standing
 * on a full cell burns; in a dark corridor a full cell sheds wisps that
 * settle in its air.
 * glow-world-goo-is-named-wisps-and-takes-the-holds-place
 * wisps-goo-glows-at-a-light-level-equal-to-its-layers
 */
@RegistersGameTests
public final class WispsTests {

    private static final String WISPS_CELL_SHINES_AT_ITS_LAYERS = "wisps_cell_shines_at_its_layers";
    private static final Identifier WISPS = Identifier.parse("goo:glow_wisps");
    /** The room spans 0 to 4 across and 0 to 4 up, stone but for the shaft over the floor block. */
    private static final int ROOM_EDGE = 4;
    /** The floor block the drips strike, in the middle of the room. */
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    /** The cell over the floor block, where the drips' layers land. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    /** Ticks the light engine takes to light the room after the cell changes. */
    private static final int LIGHT_SETTLE_TICKS = 4;
    private static final String ABILITY_REQUIRED = "The ability registry must hold glow_wisps";
    private static final String CELL_WRONG = "The cell over the floor should hold %d layers of Wisps, stands %s";
    private static final String LIGHT_WRONG = "A Wisps cell of %d layers should shine at block light %d, reads %d";
    private static final String WISPS_BURNS_A_ZOMBIE_ON_IT = "wisps_burns_a_zombie_on_it";
    private static final String ZOMBIE_UNLIT = "A zombie on a full Wisps cell should be on fire";
    private static final String ZOMBIE_UNHARMED = "A zombie on a full Wisps cell should take holy damage";
    private static final String WISPS_HOLY_DAMAGE_HURTS_A_WITHER_SKELETON = "wisps_holy_damage_hurts_a_wither_skeleton";
    /** The tall shaft's air runs over the cell up to here, under a stone roof one higher. */
    private static final int TALL_SHAFT_TOP = 4;
    private static final String SKELETON_UNHARMED =
            "A fire-immune wither skeleton on a full Wisps cell should take holy damage";
    private static final String WISPS_SHEDS_A_WISP_INTO_THE_DARK = "wisps_sheds_a_wisp_into_the_dark";
    /** The corridor's air runs 1 to 30 along x, 1 to 2 up, at z 3, inside the march bay. */
    private static final int CORRIDOR_LENGTH = 30;
    private static final int CORRIDOR_HEIGHT = 2;
    private static final int CORRIDOR_Z = 3;
    /** The full Wisps cell, on the floor at the corridor's near end. */
    private static final BlockPos CORRIDOR_CELL = new BlockPos(1, 1, CORRIDOR_Z);
    private static final String WISPS_DRIFTING_WISP_STANDS_ON_ITS_SPOT =
            "wisps_drifting_wisp_stands_on_its_spot_as_it_settles";
    private static final String WISPS_EVERY_DRIFTING_WISP_SETTLES = "wisps_every_drifting_wisp_settles";
    /** Ticks the corridor sheds for, several sheds of a full cell, before the test succeeds. */
    private static final int SHEDDING_TICKS = 400;
    /** The cave spans 0 to 15 across and 0 to 4 up inside the light bay, stone shell around its air. */
    private static final int CAVE_EDGE = 15;
    private static final int CAVE_TOP = 4;
    /** The full Wisps cell, on the cave's floor in a corner. */
    private static final BlockPos CAVE_CELL = new BlockPos(1, 1, 1);
    private static final String VANISHED = "A drifting wisp left %s with no wisp block settled there";
    private static final String SETTLED_ALONE = "The wisp block at %s appeared with no drifting wisp on its spot";
    private static final String NO_WISP_SETTLED = "A wisp shed by the Wisps cell should settle in the corridor's air";

    private WispsTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, WISPS_CELL_SHINES_AT_ITS_LAYERS,
                WispsTests::wispsCellShinesAtItsLayers);
        GooTestFunctions.registerTest(registrar, WISPS_BURNS_A_ZOMBIE_ON_IT, WispsTests::wispsBurnsAZombieOnIt);
        GooTestFunctions.registerTest(registrar, WISPS_HOLY_DAMAGE_HURTS_A_WITHER_SKELETON,
                WispsTests::wispsHolyDamageHurtsAWitherSkeleton);
        GooTestFunctions.registerTest(registrar, WISPS_SHEDS_A_WISP_INTO_THE_DARK,
                WispsTests::wispsShedsAWispIntoTheDark);
        GooTestFunctions.registerTest(registrar, WISPS_EVERY_DRIFTING_WISP_SETTLES,
                WispsTests::wispsEveryDriftingWispSettles);
        GooTestFunctions.registerTest(registrar, WISPS_DRIFTING_WISP_STANDS_ON_ITS_SPOT,
                WispsTests::wispsDriftingWispStandsOnItsSpotAsItSettles);
    }

    /**
     * A full Wisps cell at one end of a sealed dark stone corridor sheds
     * wisps that drift off down it and settle as wisp blocks in its air.
     * wisps-goo-sheds-a-wisp-toward-nearby-dark
     * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
     *
     * @param helper the gametest helper
     */
    public static void wispsShedsAWispIntoTheDark(GameTestHelper helper) {
        sealCorridorWithAFullCell(helper);
        helper.succeedWhen(() -> helper.assertTrue(!wispsInTheCorridor(helper).isEmpty(), NO_WISP_SETTLED));
    }

    /**
     * A drifting wisp still stands on its spot on the tick its wisp block
     * appears there, so a watching client never meets the spot with neither
     * the drifting mote nor the settled one drawn.
     * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
     *
     * @param helper the gametest helper
     */
    public static void wispsDriftingWispStandsOnItsSpotAsItSettles(GameTestHelper helper) {
        sealCorridorWithAFullCell(helper);
        Set<BlockPos> settled = new HashSet<>();
        helper.onEachTick(() -> {
            for (BlockPos wisp : wispsInTheCorridor(helper)) {
                if (settled.add(wisp)) {
                    int drifting = helper.getLevel().getEntitiesOfClass(DriftingWisp.class,
                            new AABB(helper.absolutePos(wisp))).size();
                    helper.assertTrue(drifting > 0, String.format(SETTLED_ALONE, wisp));
                }
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(!settled.isEmpty(), NO_WISP_SETTLED));
    }

    /**
     * Every wisp a full cell sheds into a sealed dark cave ends settled: no
     * drifting wisp leaves the world but onto a wisp block, so none heads
     * for a spot another wisp's light will reach first.
     * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
     *
     * @param helper the gametest helper
     */
    public static void wispsEveryDriftingWispSettles(GameTestHelper helper) {
        for (int x = 0; x <= CAVE_EDGE; x++) {
            for (int y = 0; y <= CAVE_TOP; y++) {
                for (int z = 0; z <= CAVE_EDGE; z++) {
                    boolean shell = x == 0 || x == CAVE_EDGE || y == 0 || y == CAVE_TOP || z == 0 || z == CAVE_EDGE;
                    helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
        helper.setBlock(CAVE_CELL, GooBlocks.GLOW_WISPS.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        Map<UUID, BlockPos> drifting = new HashMap<>();
        AABB cave = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(CAVE_EDGE, CAVE_TOP, CAVE_EDGE);
        helper.onEachTick(() -> {
            Map<UUID, BlockPos> now = new HashMap<>();
            for (DriftingWisp wisp : helper.getLevel().getEntitiesOfClass(DriftingWisp.class, cave)) {
                now.put(wisp.getUUID(), wisp.blockPosition());
            }
            drifting.forEach((id, last) -> {
                if (!now.containsKey(id)) {
                    helper.assertTrue(helper.getLevel().getBlockState(last).is(GooBlocks.WISP.get()),
                            String.format(VANISHED, helper.relativePos(last)));
                }
            });
            drifting.clear();
            drifting.putAll(now);
        });
        helper.runAtTickTime(SHEDDING_TICKS, helper::succeed);
    }

    private static void sealCorridorWithAFullCell(GameTestHelper helper) {
        for (int x = 0; x <= CORRIDOR_LENGTH + 1; x++) {
            for (int y = 0; y <= CORRIDOR_HEIGHT + 1; y++) {
                for (int z = CORRIDOR_Z - 1; z <= CORRIDOR_Z + 1; z++) {
                    boolean air = x >= 1 && x <= CORRIDOR_LENGTH && y >= 1 && y <= CORRIDOR_HEIGHT && z == CORRIDOR_Z;
                    helper.setBlock(new BlockPos(x, y, z), air ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
        helper.setBlock(CORRIDOR_CELL, GooBlocks.GLOW_WISPS.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
    }

    private static List<BlockPos> wispsInTheCorridor(GameTestHelper helper) {
        List<BlockPos> wisps = new ArrayList<>();
        for (int x = 1; x <= CORRIDOR_LENGTH; x++) {
            for (int y = 1; y <= CORRIDOR_HEIGHT; y++) {
                BlockPos air = new BlockPos(x, y, CORRIDOR_Z);
                if (helper.getBlockState(air).is(GooBlocks.WISP.get())) {
                    wisps.add(air);
                }
            }
        }
        return wisps;
    }

    /**
     * A zombie standing on a full Wisps cell, roofed from the sun in the
     * stone shaft, is set on fire and its health falls once the holy
     * damage's cadence comes round.
     * undead-touching-wisps-goo-erupt-in-flames-and-take-holy-damage-by-fill
     *
     * @param helper the gametest helper
     */
    public static void wispsBurnsAZombieOnIt(GameTestHelper helper) {
        sealRoom(helper);
        helper.setBlock(OVER_THE_FLOOR, GooBlocks.GLOW_WISPS.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, OVER_THE_FLOOR.above());
        float startingHealth = zombie.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.isOnFire(), ZOMBIE_UNLIT);
            helper.assertTrue(zombie.getHealth() < startingHealth, ZOMBIE_UNHARMED);
        });
    }

    /**
     * A wither skeleton standing on a full Wisps cell, in a stone shaft tall
     * enough to hold it and roofed from the sun, loses health once the holy
     * damage's cadence comes round: fire cannot harm it, so the health it
     * loses is goo:holy's.
     * undead-touching-wisps-goo-erupt-in-flames-and-take-holy-damage-by-fill
     *
     * @param helper the gametest helper
     */
    public static void wispsHolyDamageHurtsAWitherSkeleton(GameTestHelper helper) {
        for (int x = 0; x <= ROOM_EDGE; x++) {
            for (int y = 0; y <= TALL_SHAFT_TOP + 1; y++) {
                for (int z = 0; z <= ROOM_EDGE; z++) {
                    boolean shaft = x == FLOOR.getX() && z == FLOOR.getZ() && y > FLOOR.getY() && y <= TALL_SHAFT_TOP;
                    helper.setBlock(new BlockPos(x, y, z), shaft ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
        helper.setBlock(OVER_THE_FLOOR, GooBlocks.GLOW_WISPS.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        WitherSkeleton skeleton = helper.spawn(EntityType.WITHER_SKELETON, OVER_THE_FLOOR.above());
        float startingHealth = skeleton.getHealth();
        helper.succeedWhen(() -> helper.assertTrue(skeleton.getHealth() < startingHealth, SKELETON_UNHARMED));
    }

    /**
     * One Wisps drip striking the floor of a sealed dark room lays a Wisps
     * cell of one layer shining at block light 1; fourteen more fill the
     * cell, and it shines at 15.
     *
     * @param helper the gametest helper
     */
    public static void wispsCellShinesAtItsLayers(GameTestHelper helper) {
        sealRoom(helper);
        AbilityDefinition wisps = AbilityRegistry.of(helper.getLevel()).getAbility(WISPS);
        helper.assertTrue(wisps != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.GLOW);
        KnownRecipes.teachRequires(caster, wisps);
        LandedDrips.landOn(helper, caster, wisps, FLOOR, 1);
        helper.runAfterDelay(LIGHT_SETTLE_TICKS, () -> {
            assertShinesAt(helper, 1);
            LandedDrips.landOn(helper, caster, wisps, FLOOR, VolumeCellBlock.MAX_LAYERS - 1);
            helper.runAfterDelay(LIGHT_SETTLE_TICKS, () -> {
                helper.getLevel().getServer().getPlayerList().remove(caster);
                assertShinesAt(helper, VolumeCellBlock.MAX_LAYERS);
                helper.succeed();
            });
        });
    }

    private static void assertShinesAt(GameTestHelper helper, int layers) {
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean held = state.is(GooBlocks.GLOW_WISPS.get()) && state.getValue(VolumeCellBlock.LAYERS) == layers;
        helper.assertTrue(held, String.format(CELL_WRONG, layers, state));
        int light = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(OVER_THE_FLOOR));
        helper.assertTrue(light == layers, String.format(LIGHT_WRONG, layers, layers, light));
    }

    /**
     * Seals a stone room whose only air is the one-block shaft over the
     * floor block, so the goo has no neighbour to pour into and its layers
     * stack in the cell.
     */
    private static void sealRoom(GameTestHelper helper) {
        for (int x = 0; x <= ROOM_EDGE; x++) {
            for (int y = 0; y <= ROOM_EDGE; y++) {
                for (int z = 0; z <= ROOM_EDGE; z++) {
                    boolean shaft = x == FLOOR.getX() && z == FLOOR.getZ() && y > FLOOR.getY() && y < ROOM_EDGE;
                    helper.setBlock(new BlockPos(x, y, z), shaft ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
    }
}
