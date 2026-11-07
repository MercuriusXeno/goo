package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.PortalChoice;
import com.mercuriusxeno.goo.ability.quantum.PortalTether;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gametest for Quantum's portals: three casts at walls place three frames two
 * blocks tall; right clicking one with Portal in hand enters choosing, and
 * right clicking a distant one links the two, the first the blue entry
 * portal, both chunks tethered; standing a block off the entry carries no
 * one, pressing against its face carries the player out of its partner; a
 * later link unlinks the earlier pair (decision portals-link-by-looking-through-portal-vision).
 */
@RegistersGameTests
public final class PortalTests {
    private static final String PLACE_CHOOSE_LINK_AND_CARRY = "portals_place_choose_link_and_carry";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PLACE_CHOOSE_LINK_AND_CARRY, PortalTests::placeChooseLinkAndCarry);
        GooTestFunctions.registerTest(registrar, HIT_BREAKS_THE_PORTAL, PortalTests::hitBreaksThePortal);
    }

    private static final String HIT_BREAKS_THE_PORTAL = "portal_breaks_when_hit_and_the_wall_stands";

    /**
     * A creative player hitting a portal's face breaks the portal, as though
     * it were a block over the wall, and the wall behind it stands.
     *
     * @param helper the gametest helper
     */
    public static void hitBreaksThePortal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(level).getAbility(Identifier.parse(PORTAL)));
        player.setGameMode(GameType.CREATIVE);
        Portal portal = castAt(helper, player, FIRST, Direction.SOUTH);
        standAt(helper, player, A_BLOCK_OFF);
        player.gameMode.handleBlockBreakAction(helper.absolutePos(FIRST.above()),
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.SOUTH, level.getMaxY(), 0);
        helper.assertTrue(QuantumPortals.get(level).portalsOf(player.getUUID()).isEmpty(),
                "Hitting the portal's face should break the portal, holds " + portal);
        helper.assertTrue(level.getBlockState(helper.absolutePos(FIRST.above())).is(Blocks.STONE),
                "The wall behind a hit portal should stand");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static final String PORTAL = "goo:quantum_portal";
    private static final int NO_ENTITY = -1;
    /** The lower blocks of the three wall frames, each cast on its south or north face. */
    private static final BlockPos FIRST = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND = new BlockPos(14, 1, 14);
    private static final BlockPos THIRD = new BlockPos(14, 1, 1);
    /** Where the player waits, clear of every portal. */
    private static final BlockPos AWAY = new BlockPos(8, 1, 8);
    /** Feet a block off the first frame's face, in the cell before it: the body does not touch the face. */
    private static final Vec3 A_BLOCK_OFF = new Vec3(1.5, 1, 2.5);
    /** Feet pressed against the first frame's face, the body touching it. */
    private static final Vec3 AGAINST_THE_FACE = new Vec3(1.5, 1, 2.35);
    /** How near a frame's mouth a traveller stands once carried out of it. */
    private static final double OUT_OF = 0.6;
    private static final int STEP = 3;

    private PortalTests() {
    }

    /**
     * Places three wall frames, links two by choosing, shows a step a block
     * off carries no one and a step against the face carries, and relinks.
     *
     * @param helper the gametest helper
     */
    public static void placeChooseLinkAndCarry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(level).getAbility(Identifier.parse(PORTAL)));
        standAt(helper, player, Vec3.atBottomCenterOf(AWAY));
        Portal first = castAt(helper, player, FIRST, Direction.SOUTH);
        Portal second = castAt(helper, player, SECOND, Direction.NORTH);
        Portal third = castAt(helper, player, THIRD, Direction.SOUTH);
        QuantumPortals portals = QuantumPortals.get(level);
        helper.assertTrue(Set.copyOf(portals.portalsOf(player.getUUID())).equals(Set.of(first, second, third)),
                "Three casts should place three frames two tall, holds " + portals.portalsOf(player.getUUID()));

        PortalChoice.choose(player, Optional.of(first));
        helper.assertTrue(portals.choosing(player.getUUID()).equals(Optional.of(first)),
                "Right clicking a portal with Portal in hand should enter choosing for it");
        PortalChoice.link(player, second);
        helper.assertTrue(portals.linkOf(player.getUUID()).map(QuantumPortals.Link::entry).equals(Optional.of(first)),
                "The portal chosen from should be the blue entry portal");
        helper.assertTrue(portals.partnerOf(first).equals(Optional.of(second)),
                "Linking should pair the chosen portal with the one right clicked");
        helper.assertTrue(portals.choosing(player.getUUID()).isEmpty(), "A link landing should end choosing");
        assertTethered(helper, player, first);
        assertTethered(helper, player, second);

        helper.runAfterDelay(STEP, () -> standAt(helper, player, A_BLOCK_OFF));
        helper.runAfterDelay(STEP + STEP, () -> {
            helper.assertTrue(player.position().distanceTo(helper.absoluteVec(A_BLOCK_OFF)) < OUT_OF,
                    "Standing a block off the portal's face should carry no one, stands at " + player.position());
            standAt(helper, player, AGAINST_THE_FACE);
        });
        helper.runAfterDelay(STEP * 3, () -> {
            assertOutOf(helper, player, second);
            PortalChoice.choose(player, Optional.of(second));
            PortalChoice.link(player, third);
            helper.assertTrue(portals.partnerOf(first).isEmpty(), "A later link should unlink the earlier pair");
            helper.assertTrue(portals.partnerOf(second).equals(Optional.of(third)),
                    "The later link should pair the two it names");
            assertTethered(helper, player, third);
            ChunkPos firstChunk = ChunkPos.containing(first.block().pos());
            if (!firstChunk.equals(ChunkPos.containing(second.block().pos()))
                    && !firstChunk.equals(ChunkPos.containing(third.block().pos()))) {
                helper.assertFalse(PortalTether.CONTROLLER.forceChunk(level, player.getUUID(), firstChunk.x(),
                        firstChunk.z(), false, true), "The unlinked portal's chunk should hold no tether");
            }
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /** Builds a two-tall stone wall at a cell and casts a portal on its face, answering the frame expected. */
    private static Portal castAt(GameTestHelper helper, ServerPlayer caster, BlockPos lower, Direction face) {
        helper.setBlock(lower, Blocks.STONE);
        helper.setBlock(lower.above(), Blocks.STONE);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), caster, GooTypes.QUANTUM,
                NO_ENTITY, helper.absolutePos(lower), face, PORTAL));
        return new Portal(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(lower)), face, Direction.UP);
    }

    /** A tether stands where forcing the chunk again changes nothing. */
    private static void assertTethered(GameTestHelper helper, ServerPlayer owner, Portal portal) {
        ChunkPos chunk = ChunkPos.containing(portal.block().pos());
        helper.assertFalse(PortalTether.CONTROLLER.forceChunk(helper.getLevel(), owner.getUUID(), chunk.x(), chunk.z(),
                true, true), "A linked portal's chunk should be tethered, " + chunk + " was not");
    }

    private static void standAt(GameTestHelper helper, ServerPlayer player, Vec3 feet) {
        Vec3 at = helper.absoluteVec(feet);
        player.teleportTo(at.x, at.y, at.z);
    }

    private static void assertOutOf(GameTestHelper helper, ServerPlayer player, Portal portal) {
        double distance = player.position().distanceTo(Vec3.atBottomCenterOf(portal.mouth()));
        helper.assertTrue(distance < OUT_OF, "Pressing against the entry's face should carry the player out of its "
                + "partner, stands " + distance + " away at " + player.position());
    }
}
