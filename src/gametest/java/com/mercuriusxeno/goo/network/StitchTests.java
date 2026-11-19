package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.yarn.StitchMob;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Yarn's Stitch through the real landing its stream's drips take: a drip
 * striking stone's top lays a one-layer Stitch cell there; a cow walking a
 * lane of Stitch covers the ground a cow walking bare stone covers, and a
 * zombie walking a lane of Stitch covers less than a zombie on bare stone.
 * stitch-is-yarns-residual-that-turns-slimes-into-passive-mobs
 * passives-move-through-stitch-and-hostiles-tangle-in-it
 */
@RegistersGameTests
public final class StitchTests {

    private static final String PASSIVES_CROSS_HOSTILES_TANGLE = "stitch_lays_yarn_goo_a_cow_crosses_and_a_zombie_tangles";
    private static final Identifier STITCH = Identifier.parse("goo:yarn_stitch");
    private static final String ABILITY_REQUIRED = "The ability registry must hold yarn_stitch";
    /**
     * The floor block the cast's drip strikes, at the march bay's west end,
     * west of the lanes and clear of where the caster stands.
     */
    private static final BlockPos DRIP_FLOOR = new BlockPos(1, 0, 5);
    private static final String LAYERS_WRONG = "The cell over the stone should hold one layer of Stitch, stands %s";
    /** The two lanes across the bay, Stitch and bare stone, two blocks apart so no walker brushes another. */
    private static final int STITCH_LANE = 2;
    private static final int STONE_LANE = 4;
    /** The cows' stretch of both lanes, in the bay's west half. */
    private static final int COWS_FROM = 2;
    private static final int COWS_TO = 19;
    /** The zombies' stretch of both lanes, in the bay's east half, an air gap from the cows'. */
    private static final int ZOMBIES_FROM = 21;
    private static final int ZOMBIES_TO = 38;
    /** Three seconds, how long each walker walks before its distance is read. */
    private static final int WALK_TICKS = 60;
    /** How far apart two like walkers' distances may lie and still read as the same ground covered. */
    private static final double SAME_GROUND = 0.1;
    private static final String COW_SHOULD_MATCH = "The cow on Stitch should cover the ground the cow on stone "
            + "covers, %.3f against %.3f";
    private static final String ZOMBIE_SHOULD_LAG = "The zombie on Stitch should cover less ground than the zombie "
            + "on stone, %.3f against %.3f";
    private static final String COW_SHOULD_WALK = "The cow on stone should walk the lane, it covered %.3f";
    private static final String SLIME_BECOMES_A_PASSIVE = "slime_on_stitch_becomes_a_passive_from_the_table";
    /** The full Stitch cell the slime is set on, in the middle of the bay's floor. */
    private static final BlockPos SLIME_CELL = new BlockPos(2, 1, 2);
    /** A second, the slime's turn and its morph with room to spare. */
    private static final int TURN_TICKS = 20;
    private static final String SLIME_SHOULD_TURN = "The slime should be gone and one mob of the table stand in its "
            + "place: %d slimes, %d table mobs";
    private static final String CELL_SHOULD_SPEND = "The cell should be lower by %s's %d layers, holds %d";

    private StitchTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PASSIVES_CROSS_HOSTILES_TANGLE,
                StitchTests::stitchLaysYarnGooACowCrossesAndAZombieTangles);
        GooTestFunctions.registerTest(registrar, SLIME_BECOMES_A_PASSIVE,
                StitchTests::slimeOnStitchBecomesAPassiveFromTheTable);
    }

    /**
     * A small slime set on a full Stitch cell set into a dirt path floor is
     * gone a second later: one mob the shipped table names stands there,
     * and the cell is lower by that entry's layers.
     *
     * @param helper the gametest helper
     */
    public static void slimeOnStitchBecomesAPassiveFromTheTable(GameTestHelper helper) {
        // dirt path closes the cell in, so it pours none of its layers away, and its side, short of a full
        // block, holds no goo
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT_PATH);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
        }
        helper.setBlock(SLIME_CELL, GooBlocks.YARN_STITCH.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        Slime slime = helper.spawnWithNoFreeWill(EntityType.SLIME, Vec3.atBottomCenterOf(SLIME_CELL.above()));
        slime.setSize(1, true);
        Map<Identifier, Integer> table = helper.getLevel().registryAccess().lookupOrThrow(StitchMob.REGISTRY)
                .stream().collect(Collectors.toMap(StitchMob::entity, StitchMob::layers));
        AABB around = new AABB(helper.absolutePos(SLIME_CELL)).inflate(2);
        helper.runAfterDelay(TURN_TICKS, () -> {
            List<Slime> slimes = helper.getLevel().getEntitiesOfClass(Slime.class, around);
            List<Mob> born = helper.getLevel().getEntitiesOfClass(Mob.class, around,
                    mob -> table.containsKey(EntityType.getKey(mob.getType())));
            BlockState cell = helper.getBlockState(SLIME_CELL);
            int left = cell.is(GooBlocks.YARN_STITCH.get()) ? cell.getValue(VolumeCellBlock.LAYERS) : 0;
            helper.assertTrue(slimes.isEmpty() && born.size() == 1,
                    String.format(SLIME_SHOULD_TURN, slimes.size(), born.size()));
            int spent = table.get(EntityType.getKey(born.getFirst().getType()));
            helper.assertTrue(left == VolumeCellBlock.MAX_LAYERS - spent,
                    String.format(CELL_SHOULD_SPEND, born.getFirst().getType(), spent, left));
            helper.succeed();
        });
    }

    /**
     * One Stitch drip landing on stone's top lays a one-layer Stitch cell
     * over it. Then a cow and a zombie each walk east for three seconds along
     * a lane of one-layer Stitch cells on stone, and a cow and a zombie along
     * a lane of bare stone beside them: the cow on Stitch covers the ground
     * the cow on stone covers, and the zombie on Stitch covers less than the
     * zombie on stone.
     *
     * @param helper the gametest helper
     */
    public static void stitchLaysYarnGooACowCrossesAndAZombieTangles(GameTestHelper helper) {
        layLanes(helper);
        AbilityDefinition stitch = AbilityRegistry.of(helper.getLevel()).getAbility(STITCH);
        helper.assertTrue(stitch != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.YARN);
        KnownRecipes.teachRequires(caster, stitch);
        LandedDrips.landOn(helper, caster, stitch, DRIP_FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState laid = helper.getBlockState(DRIP_FLOOR.above());
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(laid.getBlock()).equals(STITCH)
                && laid.getValue(VolumeCellBlock.LAYERS) == 1, String.format(LAYERS_WRONG, laid));
        Block cell = laid.getBlock();
        layStitch(helper, cell, COWS_FROM, COWS_TO);
        layStitch(helper, cell, ZOMBIES_FROM, ZOMBIES_TO);
        Mob cowOnStitch = walker(helper, EntityType.COW, STITCH_LANE, COWS_FROM, COWS_TO);
        Mob cowOnStone = walker(helper, EntityType.COW, STONE_LANE, COWS_FROM, COWS_TO);
        Mob zombieOnStitch = helmeted(walker(helper, EntityType.ZOMBIE, STITCH_LANE, ZOMBIES_FROM, ZOMBIES_TO));
        Mob zombieOnStone = helmeted(walker(helper, EntityType.ZOMBIE, STONE_LANE, ZOMBIES_FROM, ZOMBIES_TO));
        double cowsStart = startX(helper, COWS_FROM);
        double zombiesStart = startX(helper, ZOMBIES_FROM);
        helper.runAfterDelay(WALK_TICKS, () -> {
            double cowStitch = cowOnStitch.getX() - cowsStart;
            double cowStone = cowOnStone.getX() - cowsStart;
            double zombieStitch = zombieOnStitch.getX() - zombiesStart;
            double zombieStone = zombieOnStone.getX() - zombiesStart;
            helper.assertTrue(cowStone > 1.0, String.format(COW_SHOULD_WALK, cowStone));
            helper.assertTrue(Math.abs(cowStitch - cowStone) <= SAME_GROUND,
                    String.format(COW_SHOULD_MATCH, cowStitch, cowStone));
            helper.assertTrue(zombieStitch < zombieStone, String.format(ZOMBIE_SHOULD_LAG, zombieStitch,
                    zombieStone));
            helper.succeed();
        });
    }

    /**
     * Lays the bay's floor as stone under open air, every lane and the cast's floor among it.
     *
     * @param helper the gametest helper
     */
    private static void layLanes(GameTestHelper helper) {
        for (int x = 0; x <= ZOMBIES_TO + 1; x++) {
            for (int z = 0; z <= STONE_LANE + 2; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    /**
     * Lays a one-layer Stitch cell on every block of the Stitch lane's stretch.
     *
     * @param helper the gametest helper
     * @param cell   the Stitch cell block
     * @param from   the stretch's west end
     * @param to     the stretch's east end
     */
    private static void layStitch(GameTestHelper helper, Block cell, int from, int to) {
        for (int x = from; x <= to; x++) {
            helper.setBlock(new BlockPos(x, 1, STITCH_LANE),
                    cell.defaultBlockState().setValue(VolumeCellBlock.LAYERS, 1));
        }
    }

    /** Where a stretch's walkers start, in the world, along the bay. */
    private static double startX(GameTestHelper helper, int from) {
        return helper.absoluteVec(new Vec3(from + 1.5, 1.0, STONE_LANE + 0.5)).x;
    }

    /**
     * Spawns a mob a block into a lane's stretch with no will of its own, on
     * the cell's one layer or on bare stone, and walks it east to the
     * stretch's end.
     *
     * @param helper the gametest helper
     * @param type   the mob's type
     * @param lane   the lane's z
     * @param from   the stretch's west end
     * @param to     the stretch's east end
     * @return the walker
     */
    private static Mob walker(GameTestHelper helper, EntityType<? extends Mob> type, int lane, int from, int to) {
        double floor = lane == STITCH_LANE ? 1.0 + 1.0 / VolumeCellBlock.MAX_LAYERS : 1.0;
        Mob mob = helper.spawnWithNoFreeWill(type, new Vec3(from + 1.5, floor, lane + 0.5));
        helper.walkTo(mob, new BlockPos(to, 1, lane), 1f);
        return mob;
    }

    /** A helmet keeps the daylight from burning the zombie. */
    private static Mob helmeted(Mob zombie) {
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }
}
