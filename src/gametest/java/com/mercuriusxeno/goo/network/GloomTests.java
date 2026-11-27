package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.GloomStep;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Nether's Gloom as a world goo through the real paths: its stream's drip
 * lays a Gloom cell, the level's own natural spawn puts a hostile mob on
 * that cell in a lit room, spending none of its goo, and the noon sun spares
 * a zombie standing on it.
 * gloom-spawns-regardless-of-light-and-mobs-on-it-ignore-daylight
 */
@RegistersGameTests
public final class GloomTests {

    private static final String SPAWNS_IN_THE_LIGHT = "gloom_cell_spawns_a_hostile_mob_in_a_lit_room";
    private static final Identifier GLOOM = Identifier.parse("goo:nether_gloom");
    private static final String ABILITY_REQUIRED = "The ability registry must hold nether_gloom";
    /** The stone the drip strikes, the floor of a room walled and roofed in glowstone. */
    private static final BlockPos DRIP_FLOOR = new BlockPos(2, 1, 2);
    private static final BlockPos CELL = DRIP_FLOOR.above();
    private static final int ROOM_EDGE = 4;
    private static final int ROOF = 5;
    /** The block light the cell must stand at for the room to count as lit, glowstone two blocks off. */
    private static final int LIT = 12;
    /** How far the caster stands off once the goo is laid: past the 24 blocks no mob spawns within, inside 128. */
    private static final double WATCHER_OFFSET = 40.0;
    /** How far the world's spawn point moves off while the spawner runs, past the 24 blocks it keeps clear. */
    private static final int SPAWN_POINT_OFFSET = 1000;
    /** The natural spawn passes run each tick, each picking its spots at random around the cell. */
    private static final int SPAWN_PASSES_PER_TICK = 64;
    private static final String CELL_LAYERS_WRONG = "The cell over the floor should be Gloom of 1 layer, stands %s";
    private static final String ROOM_DARK = "The cell should stand at block light %d or more, reads %d";
    private static final String NO_MOB = "No hostile mob spawned on the Gloom cell";
    private static final String MOB_OFF_THE_CELL = "The %s spawned should stand on the cell at %s, stands at %s";
    private static final String SPARES_FROM_THE_SUN = "a_zombie_on_gloom_does_not_burn_at_noon";
    /** The tick of the day the sun stands highest. */
    private static final long NOON = 6000L;
    /** The zombie standing in a Gloom cell, and the one two blocks off on bare stone. */
    private static final BlockPos SHADED_ZOMBIE = new BlockPos(1, 2, 2);
    private static final BlockPos BURNT_ZOMBIE = new BlockPos(3, 2, 2);
    private static final String ZOMBIE_ON_GLOOM_BURNS = "The zombie on Gloom caught fire under the noon sun";
    private static final String ZOMBIE_ON_STONE_UNLIT = "The zombie on bare stone should catch fire under the noon sun";
    private static final String GLOOM_GONE = "The Gloom cell under the shaded zombie is gone";
    private static final String SLIME_BECOMES_A_HOSTILE = "slime_on_gloom_becomes_a_random_hostile_mob";
    /** The full Gloom cell the slime is set on, in the middle of the bay's floor. */
    private static final BlockPos SLIME_CELL = new BlockPos(2, 1, 2);
    /** A second and a half, the slime's turn and its morph with room to spare. */
    private static final int TURN_TICKS = 30;
    private static final String SLIME_SHOULD_TURN = "The slime should be gone and one hostile mob stand in its "
            + "place: %d slimes, %d hostile mobs";
    private static final String CELL_SHOULD_SPEND = "The cell should be lower by the turn's %d layers, holds %d";

    private GloomTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SPAWNS_IN_THE_LIGHT, GloomTests::gloomCellSpawnsAHostileMobInALitRoom);
        GooTestFunctions.registerTest(registrar, SPARES_FROM_THE_SUN, GloomTests::aZombieOnGloomDoesNotBurnAtNoon);
        GooTestFunctions.registerTest(registrar, SLIME_BECOMES_A_HOSTILE, GloomTests::slimeOnGloomBecomesARandomHostileMob);
    }

    /**
     * A small slime set on a full Gloom cell set into a dirt path floor is
     * gone after the morph: one mob of the monster category stands there,
     * and the cell is lower by the turn's layers nether_gloom.json carries.
     * gloom-turns-slimes-into-random-hostiles-in-place-of-spawn
     *
     * @param helper the gametest helper
     */
    public static void slimeOnGloomBecomesARandomHostileMob(GameTestHelper helper) {
        // dirt path closes the cell in, so it pours none of its layers away
        for (int x = 0; x <= ROOM_EDGE; x++) {
            for (int z = 0; z <= ROOM_EDGE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT_PATH);
                for (int y = 2; y <= ROOF; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        AbilityDefinition gloom = AbilityRegistry.of(helper.getLevel()).getAbility(GLOOM);
        helper.assertTrue(gloom != null, ABILITY_REQUIRED);
        int turnLayers = GloomStep.in(gloom.behaviors()).orElseThrow().slimeTurnLayers();
        helper.setBlock(SLIME_CELL, GooBlocks.NETHER_GLOOM.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        Slime slime = helper.spawnWithNoFreeWill(EntityTypes.SLIME, Vec3.atBottomCenterOf(SLIME_CELL.above()));
        slime.setSize(1, true);
        AABB around = new AABB(helper.absolutePos(SLIME_CELL)).inflate(2);
        helper.runAfterDelay(TURN_TICKS, () -> {
            List<Slime> slimes = helper.getLevel().getEntitiesOfClass(Slime.class, around);
            List<Mob> born = helper.getLevel().getEntitiesOfClass(Mob.class, around,
                    mob -> mob.getType().getCategory() == MobCategory.MONSTER && !(mob instanceof Slime));
            BlockState cell = helper.getBlockState(SLIME_CELL);
            int left = cell.is(GooBlocks.NETHER_GLOOM.get()) ? cell.getValue(VolumeCellBlock.LAYERS) : 0;
            helper.assertTrue(slimes.isEmpty() && born.size() == 1,
                    String.format(SLIME_SHOULD_TURN, slimes.size(), born.size()));
            helper.assertTrue(left == VolumeCellBlock.MAX_LAYERS - turnLayers,
                    String.format(CELL_SHOULD_SPEND, turnLayers, left));
            helper.succeed();
        });
    }

    /**
     * Under the open noon sky, a zombie standing on a Gloom cell stays
     * unlit while one standing beside it on bare stone catches fire: the
     * sun's chance to ignite the bare one runs each tick, and the Gloom one
     * must stay unlit every tick until it does.
     *
     * @param helper the gametest helper
     */
    public static void aZombieOnGloomDoesNotBurnAtNoon(GameTestHelper helper) {
        BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(ROOM_EDGE, ROOF, ROOM_EDGE)).forEach(pos ->
                helper.setBlock(pos, pos.getY() == DRIP_FLOOR.getY() ? Blocks.STONE : Blocks.AIR));
        helper.setBlock(SHADED_ZOMBIE, GooBlocks.NETHER_GLOOM.get().defaultBlockState());
        helper.setTime(NOON);
        Zombie onGloom = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, SHADED_ZOMBIE);
        Zombie onStone = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, BURNT_ZOMBIE);
        helper.onEachTick(() -> {
            if (onGloom.isOnFire()) {
                helper.fail(ZOMBIE_ON_GLOOM_BURNS);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(SHADED_ZOMBIE).is(GooBlocks.NETHER_GLOOM.get()), GLOOM_GONE);
            helper.assertTrue(onStone.isOnFire(), ZOMBIE_ON_STONE_UNLIT);
            helper.assertFalse(onGloom.isOnFire(), ZOMBIE_ON_GLOOM_BURNS);
        });
    }

    /**
     * One Gloom drip striking the stone floor of a glowstone room lays a
     * Gloom cell of one layer; the level's natural spawn for that spot, run
     * through vanilla's spawner with the caster standing 40 blocks off, puts
     * a hostile mob on the cell, and the cell still holds its one layer.
     *
     * @param helper the gametest helper
     */
    public static void gloomCellSpawnsAHostileMobInALitRoom(GameTestHelper helper) {
        buildTheLitRoom(helper);
        AbilityDefinition gloom = AbilityRegistry.of(helper.getLevel()).getAbility(GLOOM);
        helper.assertTrue(gloom != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.NETHER);
        KnownRecipes.teachRequires(caster, gloom);
        LandedDrips.landOn(helper, caster, gloom, DRIP_FLOOR, 1);
        assertOneLayer(helper);
        caster.setPos(caster.getX() + WATCHER_OFFSET, caster.getY(), caster.getZ());
        List<Mob> spawned = new ArrayList<>();
        helper.succeedWhen(() -> {
            int light = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(CELL));
            helper.assertTrue(light >= LIT, String.format(ROOM_DARK, LIT, light));
            if (spawned.isEmpty()) {
                runNaturalSpawn(helper, spawned);
            }
            helper.assertFalse(spawned.isEmpty(), NO_MOB);
            Mob mob = spawned.getFirst();
            // defect row 26: the monster spawn puts a zombie horse, no Enemy, on the cell about one draw in 140
            helper.assertTrue(mob.getType().getCategory() == MobCategory.MONSTER
                            && mob.blockPosition().equals(helper.absolutePos(CELL)),
                    String.format(MOB_OFF_THE_CELL, mob.getType(), helper.absolutePos(CELL), mob.blockPosition()));
            assertOneLayer(helper);
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    private static void buildTheLitRoom(GameTestHelper helper) {
        BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(ROOM_EDGE, ROOF, ROOM_EDGE)).forEach(pos -> {
            boolean shell = pos.getY() == ROOF || pos.getX() % ROOM_EDGE == 0 || pos.getZ() % ROOM_EDGE == 0;
            helper.setBlock(pos, pos.getY() == DRIP_FLOOR.getY() ? Blocks.STONE
                    : shell ? Blocks.GLOWSTONE : Blocks.AIR);
        });
    }

    private static void assertOneLayer(GameTestHelper helper) {
        BlockState state = helper.getBlockState(CELL);
        helper.assertTrue(state.is(GooBlocks.NETHER_GLOOM.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1,
                String.format(CELL_LAYERS_WRONG, state));
    }

    /**
     * Runs vanilla's natural monster spawn around the cell, taking only the
     * spots on the cell, with the world's spawn point moved off for the run,
     * since a test bay may stand within the 24 blocks it keeps clear.
     */
    private static void runNaturalSpawn(GameTestHelper helper, List<Mob> spawned) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(CELL);
        LevelData.RespawnData spawnPoint = level.getRespawnData();
        level.setRespawnData(LevelData.RespawnData.of(level.dimension(), cell.offset(SPAWN_POINT_OFFSET, 0, 0),
                0.0F, 0.0F));
        try {
            for (int pass = 0; pass < SPAWN_PASSES_PER_TICK && spawned.isEmpty(); pass++) {
                NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, level, level.getChunk(cell), cell,
                        (type, spawnLevel, pos, chunk) -> pos.equals(cell), (mob, chunk) -> spawned.add(mob));
            }
        } finally {
            level.setRespawnData(spawnPoint);
        }
    }
}
