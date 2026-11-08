package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.PortalChoice;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Gametests for a portal displaced by a block: a block placed in the cell a
 * portal faces, one a portal could not be placed before, breaks the portal,
 * linked or not, as a hit breaks it
 * (decision portals-link-by-looking-through-portal-vision).
 */
@RegistersGameTests
public final class PortalDisplacedTests {
    private static final String PORTAL_BREAKS_WHEN_A_BLOCK_FILLS_ITS_FRONT =
            "portal_breaks_when_a_block_fills_its_front";
    private static final String UNPAIRED_PORTAL_BREAKS_WHEN_A_BLOCK_FILLS_ITS_FRONT =
            "unpaired_portal_breaks_when_a_block_fills_its_front";
    private static final String PORTAL_STANDS_BEHIND_A_TORCH = "portal_stands_behind_a_torch";
    private static final int WALL_X = 6;
    /** The near portal's lower block, on the wall's west face. */
    private static final BlockPos NEAR_PORTAL = new BlockPos(WALL_X, 1, 2);
    /** The far portal's lower block, on the wall's east face two blocks north. */
    private static final BlockPos FAR_PORTAL = new BlockPos(WALL_X, 1, 0);
    /** Ticks for the server's portal check to run after the placement. */
    private static final int SETTLE_TICKS = 2;

    private PortalDisplacedTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PORTAL_BREAKS_WHEN_A_BLOCK_FILLS_ITS_FRONT,
                PortalDisplacedTests::portalBreaksWhenABlockFillsItsFront);
        GooTestFunctions.registerTest(registrar, UNPAIRED_PORTAL_BREAKS_WHEN_A_BLOCK_FILLS_ITS_FRONT,
                PortalDisplacedTests::unpairedPortalBreaksWhenABlockFillsItsFront);
        GooTestFunctions.registerTest(registrar, PORTAL_STANDS_BEHIND_A_TORCH,
                PortalDisplacedTests::portalStandsBehindATorch);
    }

    /**
     * A stone placed before a linked portal's upper block breaks the portal and its link.
     *
     * @param helper the gametest helper
     */
    public static void portalBreaksWhenABlockFillsItsFront(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        Portal near = wallPortal(helper, owner, NEAR_PORTAL, Direction.WEST);
        Portal far = wallPortal(helper, owner, FAR_PORTAL, Direction.EAST);
        QuantumPortals portals = QuantumPortals.get(helper.getLevel());
        helper.assertTrue(portals.linkOf(owner).isPresent(), "The two portals should stand linked");
        helper.setBlock(NEAR_PORTAL.above().west(), Blocks.STONE);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(portals.portalAt(near.block(), near.face()).isEmpty(),
                    "A stone placed before the portal should break it");
            helper.assertTrue(portals.linkOf(owner).isEmpty(), "The broken portal's link should go with it");
            PortalChoice.breakAt(helper.getLevel(), far.block().pos(), far.face());
            helper.succeed();
        });
    }

    /**
     * A stone placed before a portal standing in no link breaks it too.
     *
     * @param helper the gametest helper
     */
    public static void unpairedPortalBreaksWhenABlockFillsItsFront(GameTestHelper helper) {
        Portal alone = wallPortal(helper, UUID.randomUUID(), NEAR_PORTAL, Direction.WEST);
        helper.setBlock(NEAR_PORTAL.west(), Blocks.STONE);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(QuantumPortals.get(helper.getLevel()).portalAt(alone.block(), alone.face()).isEmpty(),
                    "A stone placed before an unpaired portal should break it");
            helper.succeed();
        });
    }

    /**
     * A torch, which a body passes, placed before a portal leaves it standing,
     * as a portal may be placed behind one.
     *
     * @param helper the gametest helper
     */
    public static void portalStandsBehindATorch(GameTestHelper helper) {
        Portal standing = wallPortal(helper, UUID.randomUUID(), NEAR_PORTAL, Direction.WEST);
        helper.setBlock(NEAR_PORTAL.west().below(), Blocks.STONE);
        helper.setBlock(NEAR_PORTAL.west(), Blocks.TORCH);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(QuantumPortals.get(helper.getLevel()).portalAt(standing.block(), standing.face())
                    .isPresent(), "A torch before a portal should leave it standing");
            PortalChoice.breakAt(helper.getLevel(), standing.block().pos(), standing.face());
            helper.succeed();
        });
    }

    private static Portal wallPortal(GameTestHelper helper, UUID owner, BlockPos lower, Direction face) {
        for (int z = -1; z <= 3; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(WALL_X, y, z), Blocks.STONE);
            }
        }
        ServerLevel level = helper.getLevel();
        Portal portal = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(lower)), face, Direction.UP);
        PortalChoice.place(level, owner, portal);
        return portal;
    }
}
