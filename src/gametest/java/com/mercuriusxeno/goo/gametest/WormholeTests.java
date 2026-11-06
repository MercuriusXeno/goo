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
 * Gametests for Quantum's hole, a wormhole: thrown at a wall, it carries the
 * caster through to the first opening ahead a player fits in, a 3x3 or a 2x3
 * within it, and thrown where no opening lies within its reach it is refused
 * at the face and costs nothing; no block is made see-through
 * (decision hole-closes-the-space-to-an-opening-ahead).
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
    }

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
    /** Where the caster's feet come out in the full 3x3 opening: its bottom centre. */
    private static final Vec3 FULL_EXIT = new Vec3(2.5, 1, 4.5);
    /** Where the caster's feet come out in the 2x3 opening left of a stone column at x 3. */
    private static final Vec3 NARROW_EXIT = new Vec3(2.0, 1, 4.5);
    /** The column, relative, the downward throw strikes the top of. */
    private static final BlockPos COLUMN_TOP = new BlockPos(2, 0, 2);
    /** How near a spot the caster stands to count as there. */
    private static final double THERE = 0.5;

    private WormholeTests() {
    }

    /**
     * Thrown at a two-thick wall with open air past it, the hole carries the
     * caster out into the 3x3 opening behind the wall.
     *
     * @param helper the gametest helper
     */
    public static void throughToAFullOpening(GameTestHelper helper) {
        buildWall(helper);
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(STRUCK), Direction.NORTH);
        Vec3 exit = helper.absoluteVec(FULL_EXIT);
        helper.succeedWhen(() -> {
            helper.assertTrue(caster.position().distanceTo(exit) < THERE,
                    "The hole should carry the caster to " + exit + ", stands at " + caster.position());
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(STRUCK)).is(Blocks.STONE),
                    "The struck wall should stand as it was");
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    /**
     * With a stone column narrowing the opening past the wall to 2x3, the
     * hole still carries the caster out, into the 2x3 left open.
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
        Vec3 exit = helper.absoluteVec(NARROW_EXIT);
        helper.succeedWhen(() -> {
            helper.assertTrue(caster.position().distanceTo(exit) < THERE,
                    "The hole should carry the caster into the 2x3 opening at " + exit + ", stands at "
                            + caster.position());
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
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
