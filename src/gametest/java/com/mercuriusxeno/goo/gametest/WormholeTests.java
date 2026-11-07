package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Quantum's hole, a wormhole: thrown at a wall, it opens a
 * mouth on the struck face and one at the first opening ahead a player fits
 * in, a 3x3 or a 2x3 within it; stepping into either comes out of the other
 * until it closes after ten seconds; thrown where no opening lies within its
 * reach it is refused at the face and costs nothing; no block is made
 * see-through (decision hole-closes-the-space-to-an-opening-ahead).
 */
@RegistersGameTests
public final class WormholeTests {
    private static final String THROUGH_TO_A_FULL_OPENING = "hole_carries_through_to_a_full_opening";
    private static final String THROUGH_TO_A_NARROW_OPENING = "hole_carries_through_to_a_narrow_opening";
    private static final String REFUSED_WITH_NO_OPENING = "hole_refuses_with_no_opening";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, THROUGH_TO_A_FULL_OPENING, WormholeTests::throughToAFullOpening);
        GooTestFunctions.registerTest(registrar, THROUGH_TO_A_NARROW_OPENING, WormholeTests::throughToANarrowOpening);
        GooTestFunctions.registerTest(registrar, REFUSED_WITH_NO_OPENING, WormholeTests::refusedWithNoOpening);
        GooTestFunctions.registerTest(registrar, CLOSES_AFTER_ITS_LIFETIME, WormholeTests::closesAfterItsLifetime);
    }

    private static final String CLOSES_AFTER_ITS_LIFETIME = "hole_closes_after_its_lifetime";

    private static final Identifier QUANTUM_HOLE = Identifier.parse("goo:quantum_hole");
    /** quantum_hole.json's cost, which the caster holds exactly. */
    private static final int HOLE_COST = 2000;
    /** The wall block the blob strikes on its north face, the centre of the scan. */
    private static final BlockPos STRUCK = new BlockPos(2, 2, 2);
    /** Where the caster stands, north of the wall. */
    private static final Vec3 START = new Vec3(2.5, 1, 0.5);
    /** The wall's near and far z, relative: two blocks thick. */
    private static final int WALL_NEAR_Z = 2;
    private static final int WALL_FAR_Z = 3;
    /** The z, relative, of the opening past the wall. */
    private static final int OPENING_Z = 4;
    /** Where feet come out in the full 3x3 opening: its bottom centre. */
    private static final Vec3 FULL_EXIT = new Vec3(2.5, 1, 4.5);
    /** Where feet come out in the 2x3 opening left of a stone column at x 3. */
    private static final Vec3 NARROW_EXIT = new Vec3(2.0, 1, 4.5);
    /** Where feet come back out in front of the struck face: the cell before it, on the floor. */
    private static final Vec3 ENTRY_RETURN = new Vec3(2.5, 1, 1.5);
    /** Feet pressed against the struck face, the body touching it. */
    private static final Vec3 AGAINST_THE_FACE = new Vec3(2.5, 1, 1.75);
    /** Feet pressed against the wall's far face from the opening, the body touching it. */
    private static final Vec3 AGAINST_THE_FAR_FACE = new Vec3(2.5, 1, 4.25);
    /** The column, relative, the downward throw strikes the top of. */
    private static final BlockPos COLUMN_TOP = new BlockPos(2, 0, 2);
    /** How near a spot a traveller stands to count as there. */
    private static final double THERE = 0.3;
    /** Ticks the throw is given to land and open its mouths. */
    private static final int LANDED = 30;
    /** Ticks a step into a mouth is given to carry the traveller. */
    private static final int STEP = 3;
    /** quantum_hole.json's lifetime: the mouths close after ten seconds. */
    private static final int LIFETIME = 200;

    private WormholeTests() {
    }

    /**
     * Thrown at a two-thick wall with open air past it, the hole leaves the
     * caster where they stood; stepping into the face's mouth carries them
     * out into the 3x3 opening, and stepping back into the far mouth carries
     * them back out in front of the face.
     *
     * @param helper the gametest helper
     */
    public static void throughToAFullOpening(GameTestHelper helper) {
        buildWall(helper);
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(STRUCK), Direction.NORTH);
        helper.runAfterDelay(LANDED, () -> {
            assertAt(helper, caster, START, "The hole should carry no one the moment it lands");
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(STRUCK)).is(Blocks.STONE),
                    "The struck wall should stand as it was");
            standAt(helper, caster, AGAINST_THE_FACE);
        });
        helper.runAfterDelay(LANDED + STEP, () -> {
            assertAt(helper, caster, FULL_EXIT, "Stepping into the face's mouth should come out in the opening");
            standAt(helper, caster, AGAINST_THE_FAR_FACE);
        });
        helper.runAfterDelay(LANDED + STEP + STEP, () -> {
            assertAt(helper, caster, ENTRY_RETURN, "Stepping into the far mouth should come back before the face");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * With a stone column narrowing the opening past the wall to 2x3,
     * stepping into the face's mouth still comes out, into the 2x3 left open.
     *
     * @param helper the gametest helper
     */
    public static void throughToANarrowOpening(GameTestHelper helper) {
        buildWall(helper);
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(3, y, OPENING_Z), Blocks.STONE);
        }
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(STRUCK), Direction.NORTH);
        helper.runAfterDelay(LANDED, () -> standAt(helper, caster, AGAINST_THE_FACE));
        helper.runAfterDelay(LANDED + STEP, () -> {
            assertAt(helper, caster, NARROW_EXIT, "Stepping into the face's mouth should come out in the 2x3 opening");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * Once the hole's lifetime has run, its mouths are closed: stepping
     * against the struck face carries no one.
     *
     * @param helper the gametest helper
     */
    public static void closesAfterItsLifetime(GameTestHelper helper) {
        buildWall(helper);
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(STRUCK), Direction.NORTH);
        helper.runAfterDelay(LANDED + LIFETIME, () -> standAt(helper, caster, AGAINST_THE_FACE));
        helper.runAfterDelay(LANDED + LIFETIME + STEP, () -> {
            assertAt(helper, caster, AGAINST_THE_FACE, "A closed hole should carry no one");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    private static void standAt(GameTestHelper helper, ServerPlayer player, Vec3 feet) {
        Vec3 at = helper.absoluteVec(feet);
        player.teleportTo(at.x, at.y, at.z);
    }

    private static void assertAt(GameTestHelper helper, ServerPlayer player, Vec3 feet, String message) {
        Vec3 at = helper.absoluteVec(feet);
        helper.assertTrue(player.position().distanceTo(at) < THERE,
                message + ": expected " + at + ", stands at " + player.position());
    }

    /**
     * Thrown down at a stone column running to the bottom of the world, the
     * hole finds no opening within its reach: it is refused, the caster
     * stays and keeps the goo it held.
     *
     * @param helper the gametest helper
     */
    public static void refusedWithNoOpening(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = helper.absolutePos(COLUMN_TOP);
        for (int y = top.getY(); y >= level.getMinY(); y--) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlockAndUpdate(new BlockPos(top.getX() + x, y, top.getZ() + z),
                            Blocks.STONE.defaultBlockState());
                }
            }
        }
        ServerPlayer caster = caster(helper);
        Vec3 start = caster.position();
        throwAt(caster, top, Direction.UP);
        helper.assertTrue(GooSourceScanner.hasEnough(caster, GooTypes.QUANTUM, HOLE_COST),
                "A refused hole should cost nothing");
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(caster.position().distanceTo(start) < THERE,
                    "A refused hole should leave the caster where they stood, stands at " + caster.position());
            level.getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /** Ticks a refused throw is watched for, longer than any flight in the bay. */
    private static final int SETTLE_TICKS = 30;

    private static void buildWall(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = WALL_NEAR_Z; z <= WALL_FAR_Z; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    /** A survival Quantum caster holding the hole's cost exactly and knowing the hole. */
    private static ServerPlayer caster(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        caster.setGameMode(GameType.SURVIVAL);
        caster.connection.markClientLoaded();
        caster.getInventory().clearContent();
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.QUANTUM, HOLE_COST));
        KnownRecipes.teachRequires(caster, AbilityRegistry.of(caster.level()).getAbility(QUANTUM_HOLE));
        caster.snapTo(helper.absoluteVec(START));
        return caster;
    }

    private static void throwAt(ServerPlayer caster, BlockPos struck, Direction face) {
        GooGloveItem.setSelection(caster.getMainHandItem(), GloveSelection.ofAbility(GooTypes.QUANTUM, QUANTUM_HOLE));
        GooThrowHandler.execute(caster, new GooThrowPayload(GooTypes.id(GooTypes.QUANTUM), -1, struck,
                face.ordinal(), false, QUANTUM_HOLE.toString(), caster.getEyePosition()));
    }
}
