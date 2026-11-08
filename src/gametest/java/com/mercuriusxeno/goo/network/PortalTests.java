package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.PartnerStreaming;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.PortalChoice;
import com.mercuriusxeno.goo.ability.quantum.PortalTether;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Gametest for Quantum's portals: three casts at walls place three frames two
 * blocks tall; right clicking one with Portal in hand enters choosing, and
 * right clicking a distant one links the two, the first the blue entry
 * portal, both chunks tethered; standing a block off the entry carries no
 * one, going through its face carries the player on out of its partner at
 * the matching point; a
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
        GooTestFunctions.registerTest(registrar, LINK_REACHES_EVERY_CLIENT, PortalTests::linkReachesEveryClient);
        GooTestFunctions.registerTest(registrar, ARROW_FLIES_THROUGH_A_LINKED_PAIR,
                PortalTests::arrowFliesThroughALinkedPair);
        GooTestFunctions.registerTest(registrar, THROW_LANDS_THROUGH_A_LINKED_PAIR,
                PortalTests::throwLandsThroughALinkedPair);
        GooTestFunctions.registerTest(registrar, SLIME_SPAWNS_IN_PLACE_THROUGH_A_PAIR,
                PortalTests::slimeSpawnsInPlaceThroughAPair);
        GooTestFunctions.registerTest(registrar, LINK_REACHES_A_PLAYER_CHANGING_DIMENSION,
                PortalTests::linkReachesAPlayerChangingDimension);
        GooTestFunctions.registerTest(registrar, PLACING_LINKS_TO_THE_LAST_PLACED,
                PortalTests::placingLinksToTheLastPlaced);
        GooTestFunctions.registerTest(registrar, NOT_IN_A_WALL_WHILE_PASSING, PortalTests::passingBodyIsNotInAWall);
        GooTestFunctions.registerTest(registrar, OTHER_ENTITIES_GO_THROUGH, PortalTests::otherEntitiesGoThrough);
        GooTestFunctions.registerTest(registrar, BROKEN_LAST_LINKS_NOTHING,
                PortalTests::placedAfterTheLastBrokeStandsInert);
        GooTestFunctions.registerTest(registrar, LINKING_BY_HAND_COSTS_GOO, PortalTests::linkingByHandCostsGoo);
        GooTestFunctions.registerTest(registrar, ONLY_ON_OPAQUE_BLOCKS, PortalTests::opensOnlyOnOpaqueSolidBlocks);
        GooTestFunctions.registerTest(registrar, THROW_AT_NO_ROOM_DOES_NOTHING,
                PortalTests::throwWhereNoFrameFitsDoesNothing);
        GooTestFunctions.registerTest(registrar, SNEAKING_STANDS_ON_A_FLOOR_PORTAL,
                PortalTests::sneakingStandsOnAFloorPortal);
        GooTestFunctions.registerTest(registrar, FAR_SIDE_STREAMS_TO_A_LOOK, PortalTests::farSideStreamsToALook);
        GooTestFunctions.registerTest(registrar, UNLINKED_PORTAL_HOLDS_A_BODY_UP,
                PortalTests::unlinkedPortalHoldsABodyUp);
        GooTestFunctions.registerTest(registrar, FALLS_THROUGH_A_FLOOR_PAIR,
                PortalTests::floorPairCarriesAFallingBodyOutClear);
        GooTestFunctions.registerTest(registrar, PLAYER_DROPS_THROUGH_A_FLOOR_PAIR,
                PortalTests::floorPairCarriesADroppingPlayer);
    }

    private static final String PLACING_LINKS_TO_THE_LAST_PLACED = "portal_placing_links_to_the_last_placed";

    /**
     * Placing pairs unpaired portals: a portal placed alone stands inert; the
     * next pairs with it, the older the blue entry and the newer the orange
     * exit; a third, every other portal already paired, stands inert and the
     * pair stands; a fourth pairs with the unpaired third
     * (decision portals-link-by-looking-through-portal-vision).
     *
     * @param helper the gametest helper
     */
    public static void placingLinksToTheLastPlaced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(level).getAbility(Identifier.parse(PORTAL)));
        standAt(helper, player, Vec3.atBottomCenterOf(AWAY));
        QuantumPortals portals = QuantumPortals.get(level);
        Portal first = castAt(helper, player, FIRST, Direction.SOUTH);
        helper.assertTrue(portals.partnerOf(first).isEmpty(), "A portal placed alone should stand inert");
        Portal second = castAt(helper, player, SECOND, Direction.NORTH);
        helper.assertTrue(portals.linkOf(player.getUUID()).equals(Optional.of(new QuantumPortals.Link(first, second))),
                "The next portal placed should pair with the unpaired first, the older blue and the newer orange, "
                        + "holds " + portals.linkOf(player.getUUID()));
        Portal third = castAt(helper, player, THIRD, Direction.SOUTH);
        helper.assertTrue(portals.partnerOf(third).isEmpty() && portals.partnerOf(first).equals(Optional.of(second)),
                "A portal placed with every other portal paired should stand inert, the pair standing; holds "
                        + portals.linkOf(player.getUUID()));
        Portal fourth = castAt(helper, player, FOURTH, Direction.SOUTH);
        helper.assertTrue(portals.linkOf(player.getUUID()).equals(Optional.of(new QuantumPortals.Link(third, fourth))),
                "A fourth portal should pair with the unpaired third, holds " + portals.linkOf(player.getUUID()));
        assertTethered(helper, player, fourth);
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static final String NOT_IN_A_WALL_WHILE_PASSING = "portal_passing_body_is_not_in_a_wall";
    /** Feet a twentieth before the first frame's face, the eye's check reaching into the frame's blocks. */
    private static final Vec3 AT_THE_FACE = new Vec3(1.5, 1, 2.05);

    /**
     * A body pressed to a linked portal's face, passing into it, is not in
     * a wall, though the check about its eye reaches into the frame's blocks:
     * crossing neither hurts nor blinds it
     * (decision portals-link-by-looking-through-portal-vision).
     *
     * @param helper the gametest helper
     */
    public static void passingBodyIsNotInAWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(level).getAbility(Identifier.parse(PORTAL)));
        standAt(helper, player, Vec3.atBottomCenterOf(AWAY));
        castAt(helper, player, FIRST, Direction.SOUTH);
        castAt(helper, player, SECOND, Direction.NORTH);
        helper.runAfterDelay(STEP, () -> {
            standAt(helper, player, AT_THE_FACE);
            helper.assertFalse(player.isInWall(), "A body pressed to a linked portal's face should not be in a wall");
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static final String BROKEN_LAST_LINKS_NOTHING = "portal_placed_after_the_last_broke_stands_inert";

    /**
     * A portal pairs with the last portal placed, and only that one: where the
     * last placed portal broke, a new portal stands inert, never pairing with
     * an older one (decision portals-link-by-looking-through-portal-vision).
     *
     * @param helper the gametest helper
     */
    public static void placedAfterTheLastBrokeStandsInert(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(level).getAbility(Identifier.parse(PORTAL)));
        standAt(helper, player, Vec3.atBottomCenterOf(AWAY));
        QuantumPortals portals = QuantumPortals.get(level);
        Portal first = castAt(helper, player, FIRST, Direction.SOUTH);
        Portal second = castAt(helper, player, SECOND, Direction.NORTH);
        PortalChoice.breakAt(level, second.block().pos(), second.face());
        helper.assertTrue(portals.partnerOf(first).isEmpty(), "Breaking the second should leave the first unpaired");
        Portal third = castAt(helper, player, THIRD, Direction.SOUTH);
        helper.assertTrue(portals.partnerOf(third).isEmpty() && portals.partnerOf(first).isEmpty(),
                "A portal placed after the last placed broke should stand inert, holds "
                        + portals.linkOf(player.getUUID()));
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static final String LINKING_BY_HAND_COSTS_GOO = "portal_linking_by_hand_costs_goo";

    /**
     * Linking two portals by hand costs the goo a Portal throw costs: a
     * player holding it pays and the link lands; a player holding none is
     * refused, the pair standing as it was
     * (decision portals-link-by-looking-through-portal-vision).
     *
     * @param helper the gametest helper
     */
    public static void linkingByHandCostsGoo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(level).getAbility(Identifier.parse(PORTAL)));
        standAt(helper, player, Vec3.atBottomCenterOf(AWAY));
        QuantumPortals portals = QuantumPortals.get(level);
        Portal first = castAt(helper, player, FIRST, Direction.SOUTH);
        castAt(helper, player, SECOND, Direction.NORTH);
        Portal third = castAt(helper, player, THIRD, Direction.SOUTH);
        int held = com.mercuriusxeno.goo.item.GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.QUANTUM, 0);
        PortalChoice.choose(player, Optional.of(first));
        helper.assertTrue(PortalLinkPayload.linkPaid(player, third), "A player holding the goo should link by hand");
        int after = com.mercuriusxeno.goo.item.GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.QUANTUM, 0);
        helper.assertTrue(after < held, "Linking by hand should cost goo, held " + held + " then " + after);
        helper.assertTrue(portals.partnerOf(first).equals(Optional.of(third)), "The paid link should land");
        player.getInventory().clearContent();
        PortalChoice.choose(player, Optional.of(third));
        helper.assertFalse(PortalLinkPayload.linkPaid(player, first), "A player holding no goo should be refused");
        helper.assertTrue(portals.partnerOf(first).equals(Optional.of(third)), "A refused link should leave the pair");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static final String ONLY_ON_OPAQUE_BLOCKS = "portal_opens_only_on_opaque_solid_blocks";

    /**
     * A portal opens only on opaque solid blocks: cast on a wall of glass it
     * places nothing, cast on stone it places a portal
     * (decision portals-link-by-looking-through-portal-vision).
     *
     * @param helper the gametest helper
     */
    public static void opensOnlyOnOpaqueSolidBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(level).getAbility(Identifier.parse(PORTAL)));
        standAt(helper, player, Vec3.atBottomCenterOf(AWAY));
        helper.setBlock(THIRD, Blocks.GLASS);
        helper.setBlock(THIRD.above(), Blocks.GLASS);
        GooEffectScheduler.applyEffect(new PendingEffect(0, level, player, GooTypes.QUANTUM, NO_ENTITY,
                helper.absolutePos(THIRD), Direction.SOUTH, PORTAL));
        helper.assertTrue(QuantumPortals.get(level).portalsOf(player.getUUID()).isEmpty(),
                "A portal cast on glass should place nothing, holds " + QuantumPortals.get(level).portalsOf(
                        player.getUUID()));
        castAt(helper, player, FIRST, Direction.SOUTH);
        helper.assertTrue(QuantumPortals.get(level).portalsOf(player.getUUID()).size() == 1,
                "A portal cast on stone should place one");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static final String THROW_AT_NO_ROOM_DOES_NOTHING = "portal_throw_where_no_frame_fits_does_nothing";

    /**
     * A Portal throw at a face no frame fits on, a wall of glass, is refused
     * before it flies: it costs nothing and places nothing
     * (decision portals-link-by-looking-through-portal-vision).
     *
     * @param helper the gametest helper
     */
    public static void throwWhereNoFrameFitsDoesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        KnownRecipes.teachRequires(player, AbilityRegistry.of(level).getAbility(Identifier.parse(PORTAL)));
        standAt(helper, player, Vec3.atBottomCenterOf(AWAY));
        helper.setBlock(THIRD, Blocks.GLASS);
        helper.setBlock(THIRD.above(), Blocks.GLASS);
        int held = com.mercuriusxeno.goo.item.GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.QUANTUM, 0);
        com.mercuriusxeno.goo.item.GooGloveItem.setSelection(player.getMainHandItem(),
                com.mercuriusxeno.goo.ability.GloveSelection.ofAbility(GooTypes.QUANTUM, Identifier.parse(PORTAL)));
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.QUANTUM), NO_ENTITY,
                helper.absolutePos(THIRD), Direction.SOUTH.ordinal(), false, PORTAL, player.getEyePosition()));
        int after = com.mercuriusxeno.goo.item.GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.QUANTUM, 0);
        helper.assertTrue(after == held, "A Portal throw where no frame fits should cost nothing, held " + held
                + " then " + after);
        helper.runAfterDelay(THROUGH, () -> {
            helper.assertTrue(QuantumPortals.get(level).portalsOf(player.getUUID()).isEmpty(),
                    "A Portal throw where no frame fits should place nothing");
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static final String SNEAKING_STANDS_ON_A_FLOOR_PORTAL = "portal_sneaking_stands_on_a_floor_portal";
    /** Feet standing on the floor frame's top. */
    private static final Vec3 ON_THE_FLOOR_PORTAL = new Vec3(3.5, 2, 4.0);

    /**
     * A player sneaking on a floor portal stands on it as on any floor, so a
     * pair on the ground can be stepped out of; not sneaking, the frame's
     * blocks stand open to it and it drops in
     * (decision portals-link-by-looking-through-portal-vision).
     *
     * @param helper the gametest helper
     */
    public static void sneakingStandsOnAFloorPortal(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        Portal in = floorAt(helper, player, FLOOR_IN);
        floorAt(helper, player, FLOOR_OUT);
        helper.runAfterDelay(STEP, () -> {
            standAt(helper, player, ON_THE_FLOOR_PORTAL);
            BlockPos frame = in.block().pos();
            player.setShiftKeyDown(true);
            helper.assertFalse(GatePassage.passable(player, frame), "A sneaking player should stand on a floor portal");
            player.setShiftKeyDown(false);
            helper.assertTrue(GatePassage.passable(player, frame), "A player not sneaking should drop into it");
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /** A fourth wall frame's lower block. */
    private static final BlockPos FOURTH = new BlockPos(1, 1, 14);

    private static final String LINK_REACHES_EVERY_CLIENT = "portal_link_reaches_every_client";
    private static final String FAR_SIDE_STREAMS_TO_A_LOOK = "portal_far_side_streams_to_a_look";
    private static final String UNLINKED_PORTAL_HOLDS_A_BODY_UP = "portal_unlinked_holds_a_body_up";
    /** Floor frames laid on stone a block over the floor, each running south. */
    private static final BlockPos NEVER_LINKED = new BlockPos(3, 1, 3);
    private static final BlockPos LEFT_UNLINKED = new BlockPos(7, 1, 3);
    private static final BlockPos FIRST_PARTNER = new BlockPos(11, 1, 3);
    private static final BlockPos SECOND_PARTNER = new BlockPos(11, 1, 9);
    /** Ticks a body stands on a frame, long enough to fall through were it open. */
    private static final int STOOD = 20;
    /** How far below a frame's top a body's feet may sink before it counts as fallen in. */
    private static final double SINK = 0.5;

    /**
     * A portal with no partner is a floor like any other: a body standing on
     * a floor portal never linked, or one whose link a later link undid,
     * stays standing on it (decision portals-link-by-looking-through-portal-vision).
     *
     * @param helper the gametest helper
     */
    public static void unlinkedPortalHoldsABodyUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        Portal neverLinked = floorAt(helper, owner, NEVER_LINKED);
        Portal leftUnlinked = floorAt(helper, owner, LEFT_UNLINKED);
        Portal first = floorAt(helper, owner, FIRST_PARTNER);
        Portal second = floorAt(helper, owner, SECOND_PARTNER);
        PortalChoice.choose(owner, Optional.of(leftUnlinked));
        PortalChoice.link(owner, first);
        PortalChoice.choose(owner, Optional.of(first));
        PortalChoice.link(owner, second);
        helper.assertTrue(QuantumPortals.get(level).partnerOf(leftUnlinked).isEmpty()
                && QuantumPortals.get(level).partnerOf(neverLinked).isEmpty(), "Both test frames should stand unlinked");
        fenceIn(helper, NEVER_LINKED);
        fenceIn(helper, LEFT_UNLINKED);
        Pig onNever = helper.spawn(EntityType.PIG, Vec3.atBottomCenterOf(NEVER_LINKED.above()));
        Pig onLeft = helper.spawn(EntityType.PIG, Vec3.atBottomCenterOf(LEFT_UNLINKED.above()));
        helper.runAfterDelay(STOOD, () -> {
            assertStandsOn(helper, onNever, NEVER_LINKED, "never linked");
            assertStandsOn(helper, onLeft, LEFT_UNLINKED, "whose link a later link undid");
            level.getServer().getPlayerList().remove(owner);
            helper.succeed();
        });
    }

    private static final String FALLS_THROUGH_A_FLOOR_PAIR = "portal_floor_pair_carries_a_falling_body_out_clear";
    /** Two floor frames linked, each on two stone blocks with stone beneath, so nothing falls past a frame. */
    private static final BlockPos FLOOR_IN = new BlockPos(3, 1, 3);
    private static final BlockPos FLOOR_OUT = new BlockPos(11, 1, 3);
    /** Ticks a body is given to drop in and come out. */
    private static final int THROUGH = 40;

    /**
     * A body dropped onto a floor portal linked to another floor portal falls
     * in and comes out of the partner standing clear of every block, never
     * left wedged inside a frame (decision portals-link-by-looking-through-portal-vision).
     *
     * @param helper the gametest helper
     */
    public static void floorPairCarriesAFallingBodyOutClear(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        Portal in = floorAt(helper, owner, FLOOR_IN);
        Portal out = floorAt(helper, owner, FLOOR_OUT);
        PortalChoice.choose(owner, Optional.of(in));
        PortalChoice.link(owner, out);
        Pig pig = helper.spawn(EntityType.PIG, Vec3.atBottomCenterOf(FLOOR_IN.above(2)));
        helper.runAfterDelay(THROUGH, () -> {
            double top = helper.absolutePos(FLOOR_OUT).getY() + 1;
            helper.assertTrue(level.noCollision(pig, pig.getBoundingBox().deflate(EPSILON)),
                    "A body carried out of a floor portal should stand clear of every block, stands at "
                            + pig.position());
            helper.assertTrue(Math.abs(pig.getX() - helper.absoluteVec(Vec3.atCenterOf(FLOOR_OUT)).x()) < 2
                    && pig.getY() > top - SINK, "A body dropped into the floor portal should come out above its "
                    + "partner at y " + top + ", stands at " + pig.position());
            level.getServer().getPlayerList().remove(owner);
            helper.succeed();
        });
    }

    private static final String PLAYER_DROPS_THROUGH_A_FLOOR_PAIR = "portal_floor_pair_carries_a_dropping_player";
    /** Feet sunk a block into the floor frame, as deep as its one open block lets a falling body go. */
    private static final Vec3 SUNK_IN_THE_FRAME = new Vec3(3.5, 1.0, 4.0);

    /**
     * A player dropping into a floor portal sinks a block into its frame,
     * its middle past the face while its eye still stands above it; the
     * crossing its client tells the server of then carries it out of the
     * partner, clear of every block, rather than leaving it wedged in the frame
     * (decision portals-link-by-looking-through-portal-vision).
     *
     * @param helper the gametest helper
     */
    public static void floorPairCarriesADroppingPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        Portal in = floorAt(helper, player, FLOOR_IN);
        Portal out = floorAt(helper, player, FLOOR_OUT);
        PortalChoice.choose(player, Optional.of(in));
        PortalChoice.link(player, out);
        helper.runAfterDelay(STEP, () -> {
            standAt(helper, player, SUNK_IN_THE_FRAME);
            crossAsTheClientSays(player, in);
        });
        helper.runAfterDelay(STEP + STEP, () -> {
            double top = helper.absolutePos(FLOOR_OUT).getY() + 1;
            helper.assertTrue(Math.abs(player.getX() - helper.absoluteVec(Vec3.atCenterOf(FLOOR_OUT)).x()) < 2
                    && player.getY() > top - SINK, "A player sunk into the floor portal should come out above its "
                    + "partner at y " + top + ", stands at " + player.position());
            helper.assertTrue(level.noCollision(player, player.getBoundingBox().deflate(EPSILON)),
                    "A player carried out of a floor portal should stand clear of every block, stands at "
                            + player.position());
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static final String OTHER_ENTITIES_GO_THROUGH = "portal_other_entities_go_through";
    /** How hard the pig is shoved into the wall portal's face, in blocks a tick. */
    private static final double SHOVE = 0.8;

    /**
     * Bodies other than players go through portals too: an item dropped on a
     * floor portal comes out of its wall partner, and a pig shoved into a
     * wall portal comes out of its wall partner
     * (decision portals-link-by-looking-through-portal-vision).
     *
     * @param helper the gametest helper
     */
    public static void otherEntitiesGoThrough(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        Portal in = floorAt(helper, owner, FLOOR_IN);
        castAt(helper, owner, THIRD, Direction.SOUTH);
        net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level,
                helper.absoluteVec(Vec3.atBottomCenterOf(FLOOR_IN.above(2))).x(),
                helper.absoluteVec(Vec3.atBottomCenterOf(FLOOR_IN.above(2))).y(),
                helper.absoluteVec(Vec3.atBottomCenterOf(FLOOR_IN.above(2))).z(),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
        item.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(item);
        ServerPlayer other = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        KnownRecipes.teachRequires(other, AbilityRegistry.of(level).getAbility(Identifier.parse(PORTAL)));
        standAt(helper, other, Vec3.atBottomCenterOf(AWAY));
        Portal wallIn = castAt(helper, other, FIRST, Direction.SOUTH);
        Portal wallOut = castAt(helper, other, SECOND, Direction.NORTH);
        helper.assertTrue(QuantumPortals.get(level).partnerOf(wallIn).equals(Optional.of(wallOut))
                && QuantumPortals.get(level).partnerOf(in).isPresent(), "Both pairs should stand linked");
        Pig pig = helper.spawn(EntityType.PIG, A_BLOCK_OFF);
        pig.setDeltaMovement(0, 0, -SHOVE);
        helper.runAfterDelay(THROUGH, () -> {
            double itemFrom = item.position().distanceTo(helper.absoluteVec(Vec3.atCenterOf(FLOOR_IN)));
            double itemTo = item.position().distanceTo(helper.absoluteVec(Vec3.atCenterOf(THIRD)));
            helper.assertTrue(itemTo < itemFrom, "An item dropped on a floor portal should come out of its wall "
                    + "partner, lies at " + item.position());
            helper.assertTrue(pig.position().distanceTo(helper.absoluteVec(Vec3.atCenterOf(SECOND)))
                    < pig.position().distanceTo(helper.absoluteVec(Vec3.atCenterOf(FIRST))),
                    "A pig shoved into a wall portal should come out of its partner, stands at " + pig.position());
            level.getServer().getPlayerList().remove(owner);
            level.getServer().getPlayerList().remove(other);
            helper.succeed();
        });
    }

    /** How far a body's box shrinks before it is tested for blocks, so resting on a floor is clear. */
    private static final double EPSILON = 0.01;

    /**
     * Walls a floor frame's top in with glass two blocks high, so a pig set
     * on it stays on the frame rather than wandering off its edge, and falls
     * only if the frame opens under it.
     */
    private static void fenceIn(GameTestHelper helper, BlockPos first) {
        BlockPos second = first.south();
        for (int up = 1; up <= FENCE_HEIGHT; up++) {
            for (BlockPos cell : List.of(first.west(), first.east(), second.west(), second.east(), first.north(),
                    second.south())) {
                helper.setBlock(cell.above(up), Blocks.GLASS);
            }
        }
    }

    /** How high the glass around a fenced-in frame stands, past a pig's jump. */
    private static final int FENCE_HEIGHT = 2;

    /** Lays a floor frame on two stone blocks running south from a cell, placed for an owner. */
    private static Portal floorAt(GameTestHelper helper, ServerPlayer owner, BlockPos first) {
        helper.setBlock(first, Blocks.STONE);
        helper.setBlock(first.south(), Blocks.STONE);
        Portal portal = new Portal(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(first)),
                Direction.UP, Direction.SOUTH);
        PortalChoice.place(helper.getLevel(), owner.getUUID(), portal);
        return portal;
    }

    private static void assertStandsOn(GameTestHelper helper, Pig pig, BlockPos frame, String which) {
        double top = helper.absolutePos(frame).getY() + 1;
        helper.assertTrue(pig.getY() > top - SINK, "A body on a floor portal " + which + " should stand on it, "
                + "its feet stand at y " + pig.getY() + " under the frame's top at " + top);
    }
    /** How far east the far partner stands: past any view distance the server holds. */
    private static final int FAR = 480;
    /** A pitch tipping the look down into the frame's face. */
    private static final float LOOK_DOWN = 30F;
    /** A pitch looking straight up, away from every wall pair. */
    private static final float LOOK_UP = -90F;
    /** A yaw looking north, into the first frame's south face. */
    private static final float NORTH = 180F;

    /**
     * A pair linked farther apart than view distance: a player standing
     * before the entry and looking into it is streamed the partner's chunk
     * and the eight around it, and none once it looks away
     * (decision linked-partner-chunks-stream-to-any-viewer).
     *
     * @param helper the gametest helper
     */
    public static void farSideStreamsToALook(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(level).getAbility(Identifier.parse(PORTAL)));
        standAt(helper, player, Vec3.atBottomCenterOf(AWAY));
        Portal near = castAt(helper, player, FIRST, Direction.SOUTH);
        BlockPos farBlock = helper.absolutePos(FIRST).east(FAR);
        level.setBlockAndUpdate(farBlock, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(farBlock.above(), Blocks.STONE.defaultBlockState());
        Portal far = new Portal(GlobalPos.of(level.dimension(), farBlock), Direction.SOUTH, Direction.UP);
        PortalChoice.place(level, player.getUUID(), far);
        PortalChoice.choose(player, Optional.of(near));
        PortalChoice.link(player, far);
        standAt(helper, player, A_BLOCK_OFF);
        player.setYRot(NORTH);
        player.setXRot(LOOK_DOWN);
        ChunkPos partner = ChunkPos.containing(farBlock);
        Set<ChunkPos> nine = new java.util.HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                nine.add(new ChunkPos(partner.x() + dx, partner.z() + dz));
            }
        }
        // Other tests' linked pairs stand around this one in a full run, so the test reads the partner's nine
        // alone, and looks away straight up, where no wall pair stands.
        helper.runAfterDelay(STEP * 3, () -> {
            helper.assertTrue(PartnerStreaming.streamedTo(player).containsAll(nine), "A player looking into the "
                    + "entry should be streamed the nine chunks around its far partner, holds "
                    + PartnerStreaming.streamedTo(player));
            player.setXRot(LOOK_UP);
        });
        helper.runAfterDelay(STEP * 6, () -> {
            helper.assertTrue(java.util.Collections.disjoint(PartnerStreaming.streamedTo(player), nine), "A player "
                    + "looking away should hold none of the partner's chunks, holds "
                    + PartnerStreaming.streamedTo(player));
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * One player links a pair; the portal sync the server sends a second
     * player, who linked nothing, carries that link, so the second sees
     * through it like its owner (decision linked-partner-chunks-stream-to-any-viewer).
     *
     * @param helper the gametest helper
     */
    public static void linkReachesEveryClient(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        ServerPlayer onlooker = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        KnownRecipes.teachRequires(owner, AbilityRegistry.of(level).getAbility(Identifier.parse(PORTAL)));
        standAt(helper, owner, Vec3.atBottomCenterOf(AWAY));
        Portal first = castAt(helper, owner, FIRST, Direction.SOUTH);
        Portal second = castAt(helper, owner, SECOND, Direction.NORTH);
        PortalChoice.choose(owner, Optional.of(first));
        PortalChoice.link(owner, second);
        helper.assertTrue(PortalChoice.syncFor(onlooker).links().contains(new QuantumPortals.Link(first, second)),
                "The sync a player who linked nothing receives should carry another player's link, carries "
                        + PortalChoice.syncFor(onlooker).links());
        level.getServer().getPlayerList().remove(owner);
        level.getServer().getPlayerList().remove(onlooker);
        helper.succeed();
    }

    private static final String ARROW_FLIES_THROUGH_A_LINKED_PAIR = "portal_arrow_flies_through_a_linked_pair";
    /** A bow's full draw, blocks a tick. */
    private static final double ARROW_SPEED = 3.0;
    /** The share of its speed an arrow keeps coming out of the partner, its drag a tick or two in the air allowed. */
    private static final double KEPT_SPEED = 0.9;
    /** Where the arrow is loosed: three blocks before the first frame's face, square on its oval. */
    private static final Vec3 ARROW_FROM = new Vec3(1.5, 2.0, 5.0);

    /**
     * An arrow shot into a linked wall pair comes out of the partner on the
     * far side, flying away from it with its speed kept, and stays out
     * (decision aim-and-shots-carry-through-linked-gates).
     *
     * @param helper the gametest helper
     */
    public static void arrowFliesThroughALinkedPair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        standAt(helper, owner, Vec3.atBottomCenterOf(AWAY));
        Portal entry = castAt(helper, owner, FIRST, Direction.SOUTH);
        Portal exit = castAt(helper, owner, SECOND, Direction.NORTH);
        PortalChoice.choose(owner, Optional.of(entry));
        PortalChoice.link(owner, exit);
        Vec3 from = helper.absoluteVec(ARROW_FROM);
        Arrow arrow = new Arrow(level, from.x(), from.y(), from.z(), new ItemStack(Items.ARROW), null);
        arrow.setDeltaMovement(0, 0, -ARROW_SPEED);
        arrow.setNoGravity(true);
        level.addFreshEntity(arrow);
        Vec3 outward = exit.gate().face().getUnitVec3();
        // The bay is short for an arrow, which crosses it in a few ticks, so the test reads the arrow the first
        // tick it stands past the partner, then some ticks on, where it has flown on or struck the far wall.
        int[] cameOutAt = {-1};
        double[] speedOut = {0};
        helper.onEachTick(() -> {
            if (cameOutAt[0] < 0 && pastTheExit(arrow, exit)) {
                cameOutAt[0] = (int) helper.getTick();
                speedOut[0] = arrow.getDeltaMovement().dot(outward);
                helper.assertTrue(speedOut[0] > ARROW_SPEED * KEPT_SPEED, "An arrow coming out of the partner should "
                        + "fly away from it with its speed kept, flies " + arrow.getDeltaMovement());
            }
            if (cameOutAt[0] >= 0 && helper.getTick() >= cameOutAt[0] + STEP) {
                helper.assertTrue(pastTheExit(arrow, exit), "An arrow out of the partner should stay out past it, "
                        + "stands " + exit.gate().out(arrow.getBoundingBox().getCenter()) + " out of it");
                arrow.discard();
                level.getServer().getPlayerList().remove(owner);
                helper.succeed();
            }
        });
    }

    /** Whether an arrow stands out past a frame's face, in front of its oval. */
    private static boolean pastTheExit(Arrow arrow, Portal exit) {
        Vec3 middle = arrow.getBoundingBox().getCenter();
        Vec3 onTheFace = middle.subtract(exit.gate().face().getUnitVec3().scale(exit.gate().out(middle)));
        return exit.gate().out(middle) > 0 && exit.gate().withinOval(onTheFace);
    }

    private static final String THROW_LANDS_THROUGH_A_LINKED_PAIR = "portal_throw_lands_through_a_linked_pair";
    private static final Identifier CRYSTAL_PRISM = Identifier.fromNamespaceAndPath("goo", "crystal_prism");
    /** The thousands of mB of crystal goo a prism thrower carries, as StackKeyTests' thrower does. */
    private static final int PRISM_GOO = 10;
    /** How far south of the far partner the floor the throw lands on stands. */
    private static final int PAST_THE_PARTNER = 3;

    /**
     * A plain arc thrown through a linked pair 480 blocks apart lands at the
     * block it was aimed at past the partner, measured by its two legs rather
     * than the straight line between its ends, and nothing lands on the near
     * face (decision aim-and-shots-carry-through-linked-gates).
     *
     * @param helper the gametest helper
     */
    public static void throwLandsThroughALinkedPair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.CRYSTAL);
        // A prism costs more than the invoker's two thousand mB holds, so the thrower carries a prism thrower's goo.
        player.getInventory().add(GooStacks.createForOutput(GooTypes.CRYSTAL, PRISM_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, AbilityRegistry.of(level).getAbility(CRYSTAL_PRISM));
        standAt(helper, player, A_BLOCK_OFF);
        Portal near = castAt(helper, player, FIRST, Direction.SOUTH);
        BlockPos farBlock = helper.absolutePos(FIRST).east(FAR);
        level.setBlockAndUpdate(farBlock, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(farBlock.above(), Blocks.STONE.defaultBlockState());
        BlockPos floor = farBlock.south(PAST_THE_PARTNER);
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
        Portal far = new Portal(GlobalPos.of(level.dimension(), farBlock), Direction.SOUTH, Direction.UP);
        PortalChoice.place(level, player.getUUID(), far);
        PortalChoice.choose(player, Optional.of(near));
        PortalChoice.link(player, far);
        GooThrowPayload thrown = new GooThrowPayload(GooTypes.id(GooTypes.CRYSTAL), NO_ENTITY, floor,
                Direction.UP.ordinal(), false, CRYSTAL_PRISM.toString(), player.getEyePosition(),
                Vec3.atCenterOf(floor).add(0, 0.5, 0)).through(near.gate(), near.gate().center());
        GooThrowHandler.execute(player, thrown);
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBlockState(floor.above()).is(GooBlocks.PRISM.get()), "A throw through the "
                    + "linked pair should land on the floor past the partner, found "
                    + level.getBlockState(floor.above()));
            helper.assertBlockNotPresent(GooBlocks.PRISM.get(), FIRST.south());
            helper.assertBlockNotPresent(GooBlocks.PRISM.get(), FIRST.above().south());
            level.getServer().getPlayerList().remove(player);
        });
    }

    private static final String SLIME_SPAWNS_IN_PLACE_THROUGH_A_PAIR = "portal_slime_spawns_in_place_through_a_pair";
    private static final Identifier WEIRD_SLIME = Identifier.fromNamespaceAndPath("goo", "weird_slime");
    /** How far from its landing cell a slime born through a pair may stand and still be in place. */
    private static final double IN_PLACE = 1.5;
    /** Ticks the slime is watched after the throw: its flight, its morph and a little life after. */
    private static final int WATCHED_TICKS = 100;
    /** How far east of the bay the slime's far partner stands, within a mob's no-despawn reach of the thrower. */
    private static final int SLIME_FAR = 24;

    /**
     * A Slime thrown through a linked pair at a floor past the partner is
     * born on that floor and stays there, never coming in from elsewhere
     * (decision aim-and-shots-carry-through-linked-gates).
     *
     * @param helper the gametest helper
     */
    public static void slimeSpawnsInPlaceThroughAPair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.WEIRD);
        player.getInventory().add(GooStacks.createForOutput(GooTypes.WEIRD, PRISM_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, AbilityRegistry.of(level).getAbility(WEIRD_SLIME));
        standAt(helper, player, A_BLOCK_OFF);
        Portal near = castAt(helper, player, FIRST, Direction.SOUTH);
        // A hostile mob with no player within 128 blocks despawns on its first tick, so the far side stands
        // within the 32 blocks where no mob despawns at all.
        BlockPos farBlock = helper.absolutePos(FIRST).east(SLIME_FAR);
        level.setBlockAndUpdate(farBlock, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(farBlock.above(), Blocks.STONE.defaultBlockState());
        BlockPos floor = farBlock.south(PAST_THE_PARTNER);
        // The partner's tether holds only its own chunk; where the test lands it, the floor may stand in the next.
        ChunkPos floorChunk = ChunkPos.containing(floor);
        level.setChunkForced(floorChunk.x(), floorChunk.z(), true);
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(floor.above(2), Blocks.AIR.defaultBlockState());
        Portal far = new Portal(GlobalPos.of(level.dimension(), farBlock), Direction.SOUTH, Direction.UP);
        PortalChoice.place(level, player.getUUID(), far);
        PortalChoice.choose(player, Optional.of(near));
        PortalChoice.link(player, far);
        Vec3 landing = Vec3.atBottomCenterOf(floor.above());
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.WEIRD), NO_ENTITY, floor,
                Direction.UP.ordinal(), false, WEIRD_SLIME.toString(), player.getEyePosition(),
                landing).through(near.gate(), near.gate().center()));
        long thrownAt = helper.getTick();
        // A slime hops about once alive, so the test reads where it first stands, its birth.
        Vec3[] bornAt = {null};
        helper.onEachTick(() -> {
            if (bornAt[0] == null) {
                level.getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,
                        new net.minecraft.world.phys.AABB(floor).inflate(SLIME_FAR * 2)).stream().findFirst()
                        .ifPresent(slime -> bornAt[0] = slime.position());
            }
            if (helper.getTick() - thrownAt >= WATCHED_TICKS) {
                level.getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,
                        new net.minecraft.world.phys.AABB(floor).inflate(SLIME_FAR * 2)).forEach(Entity::discard);
                level.getServer().getPlayerList().remove(player);
                level.setChunkForced(floorChunk.x(), floorChunk.z(), false);
                helper.assertTrue(bornAt[0] != null, "A slime should be born past the partner");
                helper.assertTrue(bornAt[0].distanceTo(landing) <= IN_PLACE, "A slime born through a pair should "
                        + "be born where it landed, " + landing + ", was born at " + bornAt[0]);
                helper.succeed();
            }
        });
    }

    private static final String LINK_REACHES_A_PLAYER_CHANGING_DIMENSION =
            "portal_link_reaches_a_player_changing_dimension";
    /** Where in the Nether the pair stands, apart from every other test. */
    private static final BlockPos NETHER_PAIR = new BlockPos(-1024, 70, -1024);
    /** How far apart the Nether pair stands. */
    private static final int NETHER_APART = 4;

    /**
     * A pair linked in the Nether: a player in the Overworld is told no link
     * there, and once it goes to the Nether the portal sync it is told
     * carries that link, so it sees through it there
     * (decision linked-partner-chunks-stream-to-any-viewer).
     *
     * @param helper the gametest helper
     */
    public static void linkReachesAPlayerChangingDimension(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        ServerPlayer onlooker = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        UUID owner = UUID.randomUUID();
        Portal entry = netherFrame(nether, NETHER_PAIR);
        Portal exit = netherFrame(nether, NETHER_PAIR.east(NETHER_APART));
        PortalChoice.place(nether, owner, entry);
        PortalChoice.place(nether, owner, exit);
        QuantumPortals.Link link = new QuantumPortals.Link(entry, exit);
        helper.assertFalse(PortalChoice.lastSentTo(onlooker).map(sync -> sync.links().contains(link)).orElse(false),
                "A player in the Overworld should be told no link in the Nether");
        Vec3 there = Vec3.atBottomCenterOf(NETHER_PAIR.south(2));
        // Read in the same tick as the move, before another test's link resyncs every player.
        onlooker.teleportTo(nether, there.x(), there.y(), there.z(), Set.of(), 0F, 0F, false);
        boolean told = PortalChoice.lastSentTo(onlooker).map(sync -> sync.links().contains(link)).orElse(false);
        Optional<List<QuantumPortals.Link>> links = PortalChoice.lastSentTo(onlooker).map(PortalsSyncPayload::links);
        PortalChoice.breakAt(nether, entry.block().pos(), entry.face());
        PortalChoice.breakAt(nether, exit.block().pos(), exit.face());
        helper.getLevel().getServer().getPlayerList().remove(onlooker);
        helper.assertTrue(told, "A player gone to the Nether should be told the link there, told " + links);
        helper.succeed();
    }

    private static Portal netherFrame(ServerLevel nether, BlockPos block) {
        nether.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
        nether.setBlockAndUpdate(block.above(), Blocks.STONE.defaultBlockState());
        nether.setBlockAndUpdate(block.south(), Blocks.AIR.defaultBlockState());
        nether.setBlockAndUpdate(block.above().south(), Blocks.AIR.defaultBlockState());
        return new Portal(GlobalPos.of(nether.dimension(), block), Direction.SOUTH, Direction.UP);
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
    /** Feet just through the first frame's face, the body's middle a tenth behind it. */
    private static final Vec3 THROUGH_THE_FACE = new Vec3(1.5, 1, 1.9);
    /** Where feet come out of the second frame, matching the step through the first: a tenth before its face. */
    private static final Vec3 OUT_OF_THE_SECOND = new Vec3(14.5, 1, 13.9);
    /** How near a spot a traveller stands to count as there. */
    private static final double OUT_OF = 0.3;
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

        helper.runAfterDelay(STEP, () -> {
            standAt(helper, player, A_BLOCK_OFF);
            crossAsTheClientSays(player, first);
        });
        helper.runAfterDelay(STEP + STEP, () -> {
            helper.assertTrue(player.position().distanceTo(helper.absoluteVec(A_BLOCK_OFF)) < OUT_OF,
                    "A client saying it crossed from a block off the face should carry no one, stands at "
                            + player.position());
            standAt(helper, player, THROUGH_THE_FACE);
            crossAsTheClientSays(player, first);
        });
        helper.runAfterDelay(STEP * 3, () -> {
            assertOutOf(helper, player, OUT_OF_THE_SECOND);
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

    /** What the server hears from a client whose eye crossed the gate where the player now stands. */
    private static void crossAsTheClientSays(ServerPlayer player, Portal portal) {
        GatePassage.announced(player, portal.gate(), player.position(), Vec3.ZERO, player.getYRot(),
                player.getXRot());
    }

    private static void assertOutOf(GameTestHelper helper, ServerPlayer player, Vec3 feet) {
        Vec3 at = helper.absoluteVec(feet);
        helper.assertTrue(player.position().distanceTo(at) < OUT_OF, "Going through the entry's face should carry "
                + "the player on out of its partner at " + at + ", stands at " + player.position());
    }
}
