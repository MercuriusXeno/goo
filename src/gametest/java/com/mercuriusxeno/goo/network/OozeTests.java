package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.OozeStep;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Xeno's Ooze as a world goo through the real paths: a slime standing on a
 * cell holding the turn's layers becomes a random hostile mob of its biome
 * and the cell spends them, and a slime on a cell holding fewer stays a slime.
 * ooze-is-xenos-world-goo-that-turns-slimes-into-random-hostiles
 * spawn-hostile-shape-peaceful-from-a-slime
 */
@RegistersGameTests
public final class OozeTests {

    private static final String SLIME_BECOMES_A_HOSTILE = "slime_on_ooze_becomes_a_random_hostile_mob";
    private static final String THIN_OOZE_KEEPS_THE_SLIME = "a_slime_on_ooze_short_of_a_turn_stays_a_slime";
    private static final Identifier OOZE = Identifier.parse("goo:xeno_ooze");
    private static final String ABILITY_REQUIRED = "The ability registry must hold xeno_ooze";
    /** The Ooze cell the slime is set on, in the middle of the bay's floor. */
    private static final BlockPos SLIME_CELL = new BlockPos(2, 1, 2);
    private static final int BAY_EDGE = 4;
    private static final int BAY_TOP = 5;
    /** A cell's layers past a turn of 8, the one left once the turn spends them. */
    private static final int PAST_A_TURN = 9;
    /** A cell's layers short of a turn of 8. */
    private static final int SHORT_OF_A_TURN = 7;
    /** A second and a half, the slime's turn and its morph with room to spare. */
    private static final int TURN_TICKS = 30;
    private static final String SLIME_SHOULD_TURN = "The slime should be gone and one hostile mob stand in its "
            + "place: %d slimes, %d hostile mobs";
    private static final String SLIME_SHOULD_STAND = "The slime on thin Ooze should stand a slime: %d slimes, "
            + "%d hostile mobs";
    private static final String CELL_LAYERS_WRONG = "The Ooze cell should hold %d layers, holds %d";

    private OozeTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SLIME_BECOMES_A_HOSTILE, OozeTests::slimeOnOozeBecomesARandomHostileMob);
        GooTestFunctions.registerTest(registrar, THIN_OOZE_KEEPS_THE_SLIME, OozeTests::aSlimeOnOozeShortOfATurnStaysASlime);
    }

    /**
     * A small slime set on an Ooze cell of 9 layers is gone after the morph:
     * one mob of the monster category stands there, and the cell keeps the 1
     * layer the turn's 8, as xeno_ooze.json carries them, leave.
     *
     * @param helper the gametest helper
     */
    public static void slimeOnOozeBecomesARandomHostileMob(GameTestHelper helper) {
        int turnLayers = turnLayersOf(helper);
        settleASlimeOn(helper, PAST_A_TURN, (slimes, born, left) -> {
            helper.assertTrue(slimes.isEmpty() && born.size() == 1,
                    String.format(SLIME_SHOULD_TURN, slimes.size(), born.size()));
            helper.assertTrue(left == PAST_A_TURN - turnLayers,
                    String.format(CELL_LAYERS_WRONG, PAST_A_TURN - turnLayers, left));
        });
    }

    /**
     * A small slime set on an Ooze cell of 7 layers, short of the turn's 8,
     * still stands a slime after a morph's time, no hostile beside it, and
     * the cell keeps its 7.
     *
     * @param helper the gametest helper
     */
    public static void aSlimeOnOozeShortOfATurnStaysASlime(GameTestHelper helper) {
        settleASlimeOn(helper, SHORT_OF_A_TURN, (slimes, born, left) -> {
            helper.assertTrue(slimes.size() == 1 && born.isEmpty(),
                    String.format(SLIME_SHOULD_STAND, slimes.size(), born.size()));
            helper.assertTrue(left == SHORT_OF_A_TURN, String.format(CELL_LAYERS_WRONG, SHORT_OF_A_TURN, left));
        });
    }

    private static int turnLayersOf(GameTestHelper helper) {
        AbilityDefinition ooze = AbilityRegistry.of(helper.getLevel()).getAbility(OOZE);
        helper.assertTrue(ooze != null, ABILITY_REQUIRED);
        return OozeStep.in(ooze.behaviors()).orElseThrow().slimeTurnLayers();
    }

    /**
     * Lays a stone floor under dirt path, which closes the cell in so it
     * pours none of its layers away, sets an Ooze cell of the layers given
     * into it and a small slime on it, and reads what stands once a turn's
     * time has run.
     */
    private static void settleASlimeOn(GameTestHelper helper, int layers, Settled check) {
        // stone, since bedrock refuses every spawn and so every mob a turn would draw
        for (int x = 0; x <= BAY_EDGE; x++) {
            for (int z = 0; z <= BAY_EDGE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT_PATH);
                for (int y = 2; y <= BAY_TOP; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        helper.setBlock(SLIME_CELL, GooBlocks.XENO_OOZE.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, layers));
        Slime slime = helper.spawnWithNoFreeWill(EntityTypes.SLIME, Vec3.atBottomCenterOf(SLIME_CELL.above()));
        slime.setSize(1, true);
        AABB around = new AABB(helper.absolutePos(SLIME_CELL)).inflate(2);
        helper.runAfterDelay(TURN_TICKS, () -> {
            List<Slime> slimes = helper.getLevel().getEntitiesOfClass(Slime.class, around);
            List<Mob> born = helper.getLevel().getEntitiesOfClass(Mob.class, around,
                    mob -> mob.getType().getCategory() == MobCategory.MONSTER && !(mob instanceof Slime));
            BlockState cell = helper.getBlockState(SLIME_CELL);
            int left = cell.is(GooBlocks.XENO_OOZE.get()) ? cell.getValue(VolumeCellBlock.LAYERS) : 0;
            check.read(slimes, born, left);
            helper.succeed();
        });
    }

    /** What a settled slime's test reads: the slimes standing, the hostiles born and the cell's layers. */
    @FunctionalInterface
    private interface Settled {
        void read(List<Slime> slimes, List<Mob> born, int layersLeft);
    }
}
