package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.hole.PortableHoles;
import com.mercuriusxeno.goo.ability.pocket.PocketShelves;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for punching Void's hole: a punch on any block of its footprint,
 * on the face its mouth stands on, closes it at once and leaves the shelf
 * whole, and a punch beside it leaves it standing. Each punch fires through
 * the server's left-click-block hook, the path a player's punch takes.
 * decision punching-the-hole-closes-it-early
 */
@RegistersGameTests
public final class HolePunchTests {

    private static final String PUNCH_ON_ITS_FOOTPRINT_CLOSES_IT = "hole_punch_on_its_footprint_closes_it";
    private static final String PUNCH_BESIDE_IT_LEAVES_IT = "hole_punch_beside_it_leaves_it";
    /** A block, relative, outside the 3x3 footprint centred on the floor block. */
    private static final BlockPos OUTSIDE = new BlockPos(2, 0, 4);

    private HolePunchTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PUNCH_ON_ITS_FOOTPRINT_CLOSES_IT,
                HolePunchTests::punchOnItsFootprintClosesIt);
        GooTestFunctions.registerTest(registrar, PUNCH_BESIDE_IT_LEAVES_IT, HolePunchTests::punchBesideItLeavesIt);
    }

    /**
     * With a seeded shelf and a 3x3 hole open, a punch on a corner cell's top
     * face closes the hole by the next tick and the shelf keeps its kinds and
     * counts.
     *
     * @param helper the gametest helper
     */
    public static void punchOnItsFootprintClosesIt(GameTestHelper helper) {
        ServerPlayer caster = HoleTests.caster(helper, 1);
        HoleTests.layFloor(helper, cell -> true);
        PocketShelves shelves = PocketShelves.get(helper.getLevel());
        shelves.add(caster.getUUID(), new ItemStack(Items.COBBLESTONE), HoleTests.SEEDED_STONE);
        shelves.add(caster.getUUID(), new ItemStack(Items.DIAMOND), HoleTests.SEEDED_DIAMONDS);
        HoleTests.castAt(caster, helper.absolutePos(HoleTests.FLOOR), Direction.UP);
        helper.runAfterDelay(HoleTests.LANDED, () -> {
            helper.assertTrue(PortableHoles.of(caster.getUUID()).isPresent(), "The hole should stand before the punch");
            punch(helper, caster, HoleTests.CORNER);
        });
        helper.runAfterDelay(HoleTests.LANDED + 1, () -> {
            boolean stands = PortableHoles.of(caster.getUUID()).isPresent();
            long stone = shelves.countOf(caster.getUUID(), new ItemStack(Items.COBBLESTONE));
            long diamonds = shelves.countOf(caster.getUUID(), new ItemStack(Items.DIAMOND));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertFalse(stands, "A punch on the hole's corner should close it");
            helper.assertTrue(stone == HoleTests.SEEDED_STONE, "The shelf should keep " + HoleTests.SEEDED_STONE
                    + " cobblestone, keeps " + stone);
            helper.assertTrue(diamonds == HoleTests.SEEDED_DIAMONDS, "The shelf should keep "
                    + HoleTests.SEEDED_DIAMONDS + " diamonds, keeps " + diamonds);
            helper.succeed();
        });
    }

    /**
     * With a 3x3 hole open, a punch on a top face outside its footprint
     * leaves the hole standing.
     *
     * @param helper the gametest helper
     */
    public static void punchBesideItLeavesIt(GameTestHelper helper) {
        ServerPlayer caster = HoleTests.caster(helper, 1);
        HoleTests.layFloor(helper, cell -> true);
        helper.setBlock(OUTSIDE, Blocks.STONE);
        HoleTests.castAt(caster, helper.absolutePos(HoleTests.FLOOR), Direction.UP);
        helper.runAfterDelay(HoleTests.LANDED, () -> punch(helper, caster, OUTSIDE));
        helper.runAfterDelay(HoleTests.LANDED + 1, () -> {
            boolean stands = PortableHoles.of(caster.getUUID()).isPresent();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(stands, "A punch beside the hole should leave it standing");
            helper.succeed();
        });
    }

    private static void punch(GameTestHelper helper, ServerPlayer puncher, BlockPos block) {
        CommonHooks.onLeftClickBlock(puncher, helper.absolutePos(block), Direction.UP,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
    }
}
