package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveButton;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.PortalPairs;
import com.mercuriusxeno.goo.ability.quantum.PortalColor;
import com.mercuriusxeno.goo.ability.quantum.PortalShots;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.PacketRecorder;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gametest for Portal's shots: with Portal held, left click places the blue
 * portal and right click the orange one, and each lands where the look
 * strikes, twenty blocks off at an upward angle as surely as up close
 * (decisions portal-takes-over-left-and-right-click-for-blue-and-orange,
 * portal-has-no-bullet-drop-and-goes-where-you-point-like-bulb).
 */
@RegistersGameTests
public final class PortalShotTests {

    private static final String ATTACK_PLACES_BLUE_AND_USE_PLACES_ORANGE =
            "portal_attack_places_blue_and_use_places_orange";
    private static final String LANDS_WHERE_THE_LOOK_STRIKES_FAR_AND_UP =
            "portal_lands_where_the_look_strikes_far_and_up";
    private static final String A_SECOND_BLUE_MOVES_THE_BLUE_PORTAL = "portal_a_second_blue_moves_the_blue_portal";

    private static final Identifier PORTAL = Identifier.parse("goo:quantum_portal");
    private static final int NO_ENTITY = -1;
    private static final double HALF = 0.5;
    /** The shots' worth of Quantum goo a shooter holds beside what it starts with, enough for every shot a test takes. */
    private static final int SHOTS_HELD = 4;

    /** The upper block of the wall the left click aims at; its frame opens down over the block below. */
    private static final BlockPos BLUE_WALL_TOP = new BlockPos(4, 2, 2);
    /** The upper block of the wall the right click aims at. */
    private static final BlockPos ORANGE_WALL_TOP = new BlockPos(11, 2, 2);
    /** The upper block of the wall a second left click aims at, between the other two. */
    private static final BlockPos SECOND_BLUE_WALL_TOP = new BlockPos(8, 2, 2);
    /** Where the shooter stands in the light bay, before both walls' faces. */
    private static final Vec3 BEFORE_THE_WALLS = new Vec3(8.5, 1, 9.5);

    /** Where the shooter stands in the march bay. */
    private static final Vec3 FAR_FROM_THE_WALL = new Vec3(3.5, 1, 3.5);
    /** The column of the far wall, twenty blocks east of the shooter, its west face toward them. */
    private static final int FAR_WALL_X = 24;
    private static final int FAR_WALL_Z = 3;
    /** The far wall's height, up to the march bay's barrier roof, which stands seven blocks up. */
    private static final int FAR_WALL_HEIGHT = 6;
    /** The far wall's block the look strikes, above the eye and below the roof, so the look rises some 8 degrees. */
    private static final int LOOKED_AT_Y = 5;

    private PortalShotTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, ATTACK_PLACES_BLUE_AND_USE_PLACES_ORANGE,
                PortalShotTests::attackPlacesBlueAndUsePlacesOrange);
        GooTestFunctions.registerTest(registrar, LANDS_WHERE_THE_LOOK_STRIKES_FAR_AND_UP,
                PortalShotTests::landsWhereTheLookStrikesFarAndUp);
        GooTestFunctions.registerTest(registrar, A_SECOND_BLUE_MOVES_THE_BLUE_PORTAL,
                PortalShotTests::aSecondBlueMovesTheBluePortal);
        GooTestFunctions.registerTest(registrar, A_SHOT_DARTS_A_QUANTUM_BLOB, PortalShotTests::aShotDartsAQuantumBlob);
        GooTestFunctions.registerTest(registrar, NO_SHOT_PASSES_A_PORTAL, PortalShotTests::noShotPassesAPortal);
    }

    private static final String NO_SHOT_PASSES_A_PORTAL = "portal_shot_through_a_portal_places_nothing";
    /** A wall south of the blue portal, its north face toward it, which a shot out of the blue portal would strike. */
    private static final BlockPos PAST_THE_BLUE = new BlockPos(4, 2, 6);

    /**
     * A shot aimed through the shooter's orange portal, at a wall past the
     * blue one, places nothing: no portal is shot through a portal, and the
     * pair stands where it was (operator UAT 2026-11-17: you aren't supposed
     * to be able to shoot portals through portals).
     *
     * @param helper the gametest helper
     */
    public static void noShotPassesAPortal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = shooter(helper, BEFORE_THE_WALLS);
        wall(helper, BLUE_WALL_TOP, 2);
        wall(helper, ORANGE_WALL_TOP, 2);
        wall(helper, PAST_THE_BLUE, 2);
        shootAt(helper, player, GloveButton.ATTACK, BLUE_WALL_TOP, Direction.SOUTH);
        shootAt(helper, player, GloveButton.USE, ORANGE_WALL_TOP, Direction.SOUTH);
        QuantumPortals portals = QuantumPortals.get(level);
        List<Portal> pair = portals.portalsOf(player.getUUID());
        Portal orange = frameBelow(helper, ORANGE_WALL_TOP, Direction.SOUTH, PortalColor.ORANGE);
        Vec3 onTheOrange = orange.gate().center();
        player.lookAt(EntityAnchorArgument.Anchor.EYES, onTheOrange);
        PortalShots.shoot(player, GloveButton.ATTACK, aimedAt(helper, player, PAST_THE_BLUE, Direction.NORTH)
                .through(orange.gate(), onTheOrange));
        List<Portal> after = portals.portalsOf(player.getUUID());
        after.forEach(portal -> PortalPairs.breakAt(level, portal.block().pos(), portal.face()));
        level.getServer().getPlayerList().remove(player);
        helper.assertTrue(pair.size() == 2 && after.equals(pair), "A shot through the orange portal should place "
                + "nothing, the pair standing as " + pair + ", holds " + after);
        helper.succeed();
    }

    private static final String A_SHOT_DARTS_A_QUANTUM_BLOB = "portal_shot_darts_a_quantum_blob_to_the_struck_face";
    /** The visual a Portal shot names, a small fast Quantum blob. */
    private static final String QUANTUM_BLOB = "quantum_blob";
    /** How far the dart's end may stand from the struck face's middle, float rounding only. */
    private static final double ON_THE_FACE = 0.01;

    /**
     * A Portal shot sends the shooter's client a dart: a small fast Quantum
     * blob from the palm blob to the face the shot struck
     * (operator UAT 2026-11-17: have the palm blob shoot a smaller, high
     * velocity blob at the target; decision shriek-bloom-portal-mitosis-and-dart-still-throw-a-blob).
     *
     * @param helper the gametest helper
     */
    public static void aShotDartsAQuantumBlob(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = shooter(helper, BEFORE_THE_WALLS);
        PacketRecorder seen = PacketRecorder.attachClaiming(player, ScanWavePayload.class);
        wall(helper, BLUE_WALL_TOP, 2);
        shootAt(helper, player, GloveButton.ATTACK, BLUE_WALL_TOP, Direction.SOUTH);
        List<ScanWavePayload> darts = seen.payloadsOf(ScanWavePayload.class);
        Vec3 struck = faceCenter(helper, BLUE_WALL_TOP, Direction.SOUTH);
        QuantumPortals.get(level).portalsOf(player.getUUID())
                .forEach(portal -> PortalPairs.breakAt(level, portal.block().pos(), portal.face()));
        level.getServer().getPlayerList().remove(player);
        helper.assertTrue(darts.size() == 1 && darts.getFirst().visual().equals(QUANTUM_BLOB)
                && darts.getFirst().to().distanceTo(struck) < ON_THE_FACE, "A Portal shot should dart one quantum "
                + "blob to the struck face " + struck + ", darts " + darts);
        helper.succeed();
    }

    /**
     * One player shoots blue at one wall, orange at a second and blue at a
     * third: two portals stand for them, the blue one moved to the third wall
     * and the orange one on the second, each the other's partner
     * (decision player-holds-one-pair-of-portals).
     *
     * @param helper the gametest helper
     */
    public static void aSecondBlueMovesTheBluePortal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = shooter(helper, BEFORE_THE_WALLS);
        wall(helper, BLUE_WALL_TOP, 2);
        wall(helper, ORANGE_WALL_TOP, 2);
        wall(helper, SECOND_BLUE_WALL_TOP, 2);
        shootAt(helper, player, GloveButton.ATTACK, BLUE_WALL_TOP, Direction.SOUTH);
        shootAt(helper, player, GloveButton.USE, ORANGE_WALL_TOP, Direction.SOUTH);
        shootAt(helper, player, GloveButton.ATTACK, SECOND_BLUE_WALL_TOP, Direction.SOUTH);
        QuantumPortals portals = QuantumPortals.get(level);
        List<Portal> placed = portals.portalsOf(player.getUUID());
        Portal blue = frameBelow(helper, SECOND_BLUE_WALL_TOP, Direction.SOUTH, PortalColor.BLUE);
        Portal orange = frameBelow(helper, ORANGE_WALL_TOP, Direction.SOUTH, PortalColor.ORANGE);
        boolean linked = portals.partnerOf(blue).equals(Optional.of(orange))
                && portals.partnerOf(orange).equals(Optional.of(blue));
        placed.forEach(portal -> PortalPairs.breakAt(level, portal.block().pos(), portal.face()));
        level.getServer().getPlayerList().remove(player);
        helper.assertTrue(Set.copyOf(placed).equals(Set.of(blue, orange)) && placed.size() == 2, "Two portals "
                + "should stand, blue on the third wall and orange on the second, holds " + placed);
        helper.assertTrue(linked, "The blue and orange portals should each name the other as partner");
        helper.succeed();
    }

    /**
     * A player holding Portal shoots with the attack button at one wall and
     * with the use button at another: a blue portal stands on the first and
     * an orange one on the second
     * (decision portal-takes-over-left-and-right-click-for-blue-and-orange).
     *
     * @param helper the gametest helper
     */
    public static void attackPlacesBlueAndUsePlacesOrange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = shooter(helper, BEFORE_THE_WALLS);
        wall(helper, BLUE_WALL_TOP, 2);
        wall(helper, ORANGE_WALL_TOP, 2);
        shootAt(helper, player, GloveButton.ATTACK, BLUE_WALL_TOP, Direction.SOUTH);
        shootAt(helper, player, GloveButton.USE, ORANGE_WALL_TOP, Direction.SOUTH);
        List<Portal> placed = QuantumPortals.get(level).portalsOf(player.getUUID());
        Portal blue = frameBelow(helper, BLUE_WALL_TOP, Direction.SOUTH, PortalColor.BLUE);
        Portal orange = frameBelow(helper, ORANGE_WALL_TOP, Direction.SOUTH, PortalColor.ORANGE);
        placed.forEach(portal -> PortalPairs.breakAt(level, portal.block().pos(), portal.face()));
        level.getServer().getPlayerList().remove(player);
        helper.assertTrue(placed.contains(blue), "The attack button should place a blue portal, holds " + placed);
        helper.assertTrue(placed.contains(orange), "The use button should place an orange portal, holds " + placed);
        helper.succeed();
    }

    /**
     * A player holding Portal looks up at a wall twenty blocks off and
     * shoots, its client having aimed the shot at the wall's foot as a
     * dropping throw would: the portal opens on the face the straight line of
     * the look strikes
     * (decision portal-has-no-bullet-drop-and-goes-where-you-point-like-bulb).
     *
     * @param helper the gametest helper
     */
    public static void landsWhereTheLookStrikesFarAndUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = shooter(helper, FAR_FROM_THE_WALL);
        BlockPos wallTop = new BlockPos(FAR_WALL_X, FAR_WALL_HEIGHT, FAR_WALL_Z);
        wall(helper, wallTop, FAR_WALL_HEIGHT);
        BlockPos looked = new BlockPos(FAR_WALL_X, LOOKED_AT_Y, FAR_WALL_Z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, faceCenter(helper, looked, Direction.WEST));
        BlockPos foot = new BlockPos(FAR_WALL_X, 1, FAR_WALL_Z);
        PortalShots.shoot(player, GloveButton.ATTACK, aimedAt(helper, player, foot, Direction.WEST));
        List<Portal> placed = QuantumPortals.get(level).portalsOf(player.getUUID());
        Portal struck = frameBelow(helper, looked, Direction.WEST, PortalColor.BLUE);
        placed.forEach(portal -> PortalPairs.breakAt(level, portal.block().pos(), portal.face()));
        level.getServer().getPlayerList().remove(player);
        helper.assertTrue(placed.equals(List.of(struck)), "The portal should open on the face the look strikes, "
                + struck.block().pos() + ", holds " + placed + ", the look rising "
                + String.format("%.1f", -player.getXRot()) + " degrees");
        helper.succeed();
    }

    /**
     * A player holding a glove with Portal selected and known, and Quantum
     * goo to pay for every shot, standing at a spot.
     */
    private static ServerPlayer shooter(GameTestHelper helper, Vec3 feet) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM, PORTAL);
        player.getInventory().add(GooStacks.createForOutput(GooTypes.QUANTUM, SHOTS_HELD * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, AbilityRegistry.of(helper.getLevel()).getAbility(PORTAL));
        Vec3 at = helper.absoluteVec(feet);
        player.teleportTo(at.x, at.y, at.z);
        return player;
    }

    /** A stone column from the floor up to a top block. */
    private static void wall(GameTestHelper helper, BlockPos top, int height) {
        for (int below = 0; below < height; below++) {
            helper.setBlock(top.below(below), Blocks.STONE);
        }
    }

    /** Turns the player's look on a block's face and shoots, its client aiming the shot there. */
    private static void shootAt(GameTestHelper helper, ServerPlayer player, GloveButton button, BlockPos block,
                                Direction face) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, faceCenter(helper, block, face));
        PortalShots.shoot(player, button, aimedAt(helper, player, block, face));
    }

    /** The cast a client sends aimed at a block's face. */
    private static GooThrowPayload aimedAt(GameTestHelper helper, ServerPlayer player, BlockPos block,
                                           Direction face) {
        return new GooThrowPayload(GooTypes.id(GooTypes.QUANTUM), NO_ENTITY, helper.absolutePos(block),
                face.ordinal(), false, PORTAL.toString(), player.getEyePosition(), faceCenter(helper, block, face));
    }

    /** The middle of a block's face, in the level. */
    private static Vec3 faceCenter(GameTestHelper helper, BlockPos block, Direction face) {
        return Vec3.atCenterOf(helper.absolutePos(block)).add(face.getUnitVec3().scale(HALF));
    }

    /** The wall frame a shot at a block opens: down over the block below it. */
    private static Portal frameBelow(GameTestHelper helper, BlockPos struck, Direction face, PortalColor color) {
        return new Portal(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(struck.below())), face,
                Direction.UP, color);
    }
}
