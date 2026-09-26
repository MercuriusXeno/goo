package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.TapHost;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapDrip;
import com.mercuriusxeno.goo.block.tap.TapDripGrade;
import com.mercuriusxeno.goo.block.tap.TapDripScheduler;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooDripParticleOptions;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gametests for the tap's drip: what it draws, from where, and where it lands.
 */
public final class TapDripTests {

    private static final BlockPos TAP_POS = new BlockPos(1, 1, 1);
    private static final ResourceKey<GooTypeDefinition> TYPE = GooTypes.ROCK;
    private static final int START_VOLUME = 1000;
    private static final int DRIPS = 3;
    /**
     * The grade the drip tests run at, fast enough to keep them short.
     */
    private static final TapDripGrade TEST_GRADE = TapDripGrade.ONE_PER_16_TICKS;
    private static final int DRIP_INTERVAL = TEST_GRADE.intervalTicks();
    /**
     * Ticks past the last counted drip, fewer than an interval, so timing
     * slack in the test's first tick cannot add or drop a drip.
     */
    private static final int SETTLE_TICKS = 5;
    private static final int NEIGHBOR_SLOT = 4;
    private static final String TAP_VOLUME = "tap canister volume";
    private static final String NEIGHBOR_VOLUME = "neighbor canister volume";
    private static final String CLOSED_VOLUME = "tap canister volume behind a closed valve";
    private static final String OPENED_VOLUME = "tap canister volume one interval after opening";

    private static final BlockPos HIGH_TAP_POS = new BlockPos(1, 3, 1);
    private static final int AIR_GAP = 2;
    private static final String LANDING_POS = "pending drip landing pos";
    private static final String LANDING_FACE = "pending drip landing face";
    private static final String BOTTOMLESS_VOLUME = "tap canister volume over a bottomless drop";
    private static final String BOTTOMLESS_PENDING = "drips queued over a bottomless drop";

    private static final Identifier GLASS = Identifier.withDefaultNamespace("glass");
    private static final double ENTITY_SCAN_RADIUS = 3;
    private static final String NO_TAP_ABILITY = "a bundled type carries no tap ability at this commit";
    private static final String NO_ABILITY_PENDING = "drips still in flight after the fall";
    private static final String NEIGHBOR_STATE = "landing block or neighbor state";
    private static final String NO_ABILITY_ENTITIES = "entities around the landing";
    private static final BlockPos OPEN_LANDING = new BlockPos(1, 0, 1);
    private static final BlockPos COVERED_LANDING = new BlockPos(3, 0, 1);

    /** South-facing tap: a point inside the valve, in pixels. */
    private static final Vec3 VALVE_HIT_PX = new Vec3(8, 5, 8);
    private static final double PIXELS_PER_BLOCK = 16.0;
    /** South-facing tap: a point on the body, off the valve and the slot, in pixels. */
    private static final Vec3 BODY_HIT_PX = new Vec3(8, 2, 3);
    private static final String GASKET_INSTALLED = "the gasket item's click installs the tap's gasket";
    private static final String GASKET_KEPT = "gasket still installed after sneak valve click ";
    private static final String GASKET_POPPED = "a sneaking body click pops the tap's gasket";
    private static final int VALVE_CLICKS = 6;
    private static final String VALVE_OPEN = "valve open after click ";
    private static final String VALVE_GRADE = "drip grade after click ";

    private static final BlockPos OTHER_TYPE_CANISTER_POS = new BlockPos(3, 0, 1);
    private static final int ONE_TO_FOUR_TICKS = 10;
    private static final int ONE_TO_FOUR_MOVED = 40;
    private static final String ONE_TO_FOUR_CANISTER = "canister volume after 10 ticks at 1:4";
    private static final String CRUCIBLE_DRIPS_DRAWN = "tap canister drew its drips into the crucible";
    private static final String CRUCIBLE_PENDING = "drips still in flight to the crucible";
    private static final String CRUCIBLE_RESERVOIR = "crucible reservoir blaze volume";
    private static final String RECEPTACLE_PROGRAMS = "programs a drip into a crucible runs";
    private static final String RECEPTACLE_ABILITY_RUNS = "tap ability runs for a drip into a crucible";
    private static final String REFUSED_PROGRAMS = "programs a drip onto a refusing block runs";
    private static final String REFUSED_ABILITY_RUNS = "tap ability runs for a drip onto a refusing block";
    private static final String REFUSED_CANISTER_TYPE = "type a canister of another type holds after the drip";

    private static final String SENT_COUNT = "particles one tap drip sends";
    private static final String SENT_TYPE = "particle type one tap drip sends";
    private static final String SENT_GOO_TYPE = "goo type one tap drip's particle carries";

    private TapDripTests() {
    }

    /**
     * One tap drip sends the tap-drip particle, not the trail-drip, naming the
     * goo type drawn so the client draws that type's fluid sprite
     * (decisions tap-drip-own-square-particles, particles-render-muted-goo-texture).
     *
     * @param helper the gametest helper
     */
    public static void tapDripSendsTapDrip(GameTestHelper helper) {
        List<ParticleOptions> sent = new ArrayList<>();
        TapDrip.emit((option, at, velocity) -> sent.add(option), TYPE, Vec3.ZERO);
        helper.assertValueEqual(sent.size(), 1, SENT_COUNT);
        helper.assertValueEqual(sent.getFirst().getType(), GooParticles.TAP_DRIP.get(), SENT_TYPE);
        helper.assertTrue(sent.getFirst() instanceof GooDripParticleOptions drip && drip.gooType() == TYPE,
                SENT_GOO_TYPE);
        helper.succeed();
    }

    /**
     * A tap over a filled canister, ringed by filled canister blocks, loses
     * 1 mB per interval from its own canister and from nowhere else.
     *
     * @param helper the gametest helper
     */
    public static void tapDripDrawsOneMb(GameTestHelper helper) {
        List<BlockPos> neighbors = Direction.Plane.HORIZONTAL.stream()
                .map(TAP_POS::relative).toList();
        for (BlockPos neighbor : neighbors) {
            helper.setBlock(neighbor, GooBlocks.CANISTER.get());
            CanisterBlockEntity canister = helper.getBlockEntity(neighbor, CanisterBlockEntity.class);
            canister.insertCanister(NEIGHBOR_SLOT, new ItemStack(GooItems.CANISTER.get()), false);
            canister.insertGoo(NEIGHBOR_SLOT, TYPE, START_VOLUME);
        }
        TapBlockEntity tap = filledTap(helper);

        helper.runAfterDelay(DRIPS * DRIP_INTERVAL + SETTLE_TICKS, () -> {
            helper.assertValueEqual(tap.getFluidContent().amount(), START_VOLUME - DRIPS, TAP_VOLUME);
            for (BlockPos neighbor : neighbors) {
                CanisterBlockEntity canister = helper.getBlockEntity(neighbor, CanisterBlockEntity.class);
                helper.assertValueEqual(canister.getSlotFluidContent(NEIGHBOR_SLOT).amount(), START_VOLUME,
                        NEIGHBOR_VOLUME);
            }
            helper.succeed();
        });
    }

    /**
     * A closed valve drips nothing however long the tap ticks; opening it
     * drips on the next full interval.
     *
     * @param helper the gametest helper
     */
    public static void tapValveGatesDrip(GameTestHelper helper) {
        TapBlockEntity tap = filledTap(helper, false);
        int closedTicks = DRIPS * DRIP_INTERVAL + SETTLE_TICKS;

        helper.runAfterDelay(closedTicks, () -> {
            helper.assertValueEqual(tap.getFluidContent().amount(), START_VOLUME, CLOSED_VOLUME);
            helper.setBlock(TAP_POS, helper.getBlockState(TAP_POS).setValue(TapBlock.OPEN, true));
        });
        helper.runAfterDelay(closedTicks + DRIP_INTERVAL + SETTLE_TICKS, () -> {
            helper.assertValueEqual(tap.getFluidContent().amount(), START_VOLUME - 1, OPENED_VOLUME);
            helper.succeed();
        });
    }

    /**
     * A tap with its spigot two air blocks above stone queues a drip landing
     * on the stone's top face, and the drip leaves the queue once it lands.
     *
     * @param helper the gametest helper
     */
    public static void tapDripLandsBelow(GameTestHelper helper) {
        filledTap(helper, HIGH_TAP_POS, true, AIR_GAP);
        BlockPos stone = helper.absolutePos(HIGH_TAP_POS.below(AIR_GAP + 1));
        BlockPos tapAbs = helper.absolutePos(HIGH_TAP_POS);
        AtomicBoolean seen = new AtomicBoolean();

        helper.onEachTick(() -> {
            List<TapDripScheduler.PendingDrip> mine = TapDripScheduler.pending().stream()
                    .filter(drip -> drip.level() == helper.getLevel() && drip.tapPos().equals(tapAbs))
                    .toList();
            if (!mine.isEmpty()) {
                helper.assertValueEqual(mine.getFirst().landingPos(), stone, LANDING_POS);
                helper.assertValueEqual(mine.getFirst().face(), Direction.UP, LANDING_FACE);
                seen.set(true);
            } else if (seen.get()) {
                helper.succeed();
            }
        });
    }

    /**
     * A tap over a column of air down to the level's lowest block drips
     * nothing: no goo leaves its canister and no drip is queued.
     *
     * @param helper the gametest helper
     */
    public static void tapDripBottomless(GameTestHelper helper) {
        TapBlockEntity tap = filledTap(helper, TAP_POS, true, 0);
        BlockPos tapAbs = helper.absolutePos(TAP_POS);
        ServerLevel level = helper.getLevel();
        for (BlockPos.MutableBlockPos cursor = tapAbs.below().mutable();
                cursor.getY() >= level.getMinY(); cursor.move(Direction.DOWN)) {
            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        helper.runAfterDelay(DRIPS * DRIP_INTERVAL + SETTLE_TICKS, () -> {
            helper.assertValueEqual(tap.getFluidContent().amount(), START_VOLUME, BOTTOMLESS_VOLUME);
            helper.assertValueEqual(TapDripScheduler.pending().stream()
                    .filter(drip -> drip.tapPos().equals(tapAbs)).count(), 0L, BOTTOMLESS_PENDING);
            helper.succeed();
        });
    }

    /**
     * The tap host placing a block at a landing writes the block
     * into the cell above the landing, and leaves a standing block there
     * untouched.
     *
     * @param helper the gametest helper
     */
    public static void tapHostPlacesAboveLanding(GameTestHelper helper) {
        BlockPos openLanding = OPEN_LANDING;
        BlockPos coveredLanding = COVERED_LANDING;
        helper.setBlock(openLanding, Blocks.STONE);
        helper.setBlock(openLanding.above(), Blocks.AIR);
        helper.setBlock(coveredLanding, Blocks.STONE);
        helper.setBlock(coveredLanding.above(), GooBlocks.TAP.get());
        for (BlockPos landing : List.of(openLanding, coveredLanding)) {
            new TapHost(helper.getLevel(), helper.absolutePos(landing), Direction.UP).placeBlock(GLASS, Map.of());
        }

        helper.assertBlockPresent(Blocks.GLASS, openLanding.above());
        helper.assertBlockPresent(GooBlocks.TAP.get(), coveredLanding.above());
        helper.succeed();
    }

    /**
     * A drip of a type carrying no tap ability draws its 1 mB and lands,
     * and the world around the landing stays as it was, with no entity.
     *
     * @param helper the gametest helper
     */
    public static void tapDripNoAbility(GameTestHelper helper) {
        helper.assertTrue(AbilityRegistry.tapAbilityFor(TYPE) == null, NO_TAP_ABILITY);
        TapBlockEntity tap = filledTap(helper);
        BlockPos stone = TAP_POS.below();
        Map<BlockPos, BlockState> before = new HashMap<>();
        before.put(stone, helper.getBlockState(stone));
        for (Direction side : Direction.values()) {
            before.put(stone.relative(side), helper.getBlockState(stone.relative(side)));
        }
        BlockPos tapAbs = helper.absolutePos(TAP_POS);
        AABB around = new AABB(helper.absolutePos(stone)).inflate(ENTITY_SCAN_RADIUS);

        helper.runAfterDelay(DRIP_INTERVAL + SETTLE_TICKS, () -> {
            helper.assertValueEqual(tap.getFluidContent().amount(), START_VOLUME - 1, TAP_VOLUME);
            helper.assertValueEqual(TapDripScheduler.pending().stream()
                    .filter(drip -> drip.tapPos().equals(tapAbs)).count(), 0L, NO_ABILITY_PENDING);
            before.forEach((pos, state) -> helper.assertValueEqual(helper.getBlockState(pos), state, NEIGHBOR_STATE));
            helper.assertValueEqual(helper.getLevel().getEntities((Entity) null, around, entity -> true).size(), 0,
                    NO_ABILITY_ENTITIES);
            helper.succeed();
        });
    }

    /**
     * Six empty-hand clicks on a closed tap's valve step it through 64:1,
     * 16:1, 4:1, 1:1 and 1:4 and back to off
     * (decision five-rates-in-fourfold-steps).
     *
     * @param helper the gametest helper
     */
    public static void tapValveStepsFiveGrades(GameTestHelper helper) {
        helper.setBlock(TAP_POS, GooBlocks.TAP.get().defaultBlockState().setValue(TapBlock.OPEN, false));
        TapBlockEntity tap = helper.getBlockEntity(TAP_POS, TapBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult valveHit = tapHit(helper, VALVE_HIT_PX);
        List<TapDripGrade> expected = List.of(TapDripGrade.ONE_PER_64_TICKS, TapDripGrade.ONE_PER_16_TICKS,
                TapDripGrade.ONE_PER_4_TICKS, TapDripGrade.ONE_PER_TICK, TapDripGrade.FOUR_PER_TICK);

        for (int click = 1; click <= VALVE_CLICKS; click++) {
            helper.useBlock(TAP_POS, player, valveHit);
            boolean open = helper.getBlockState(TAP_POS).getValue(TapBlock.OPEN);
            boolean expectOpen = click <= expected.size();
            helper.assertValueEqual(open, expectOpen, VALVE_OPEN + click);
            if (expectOpen) {
                helper.assertValueEqual(tap.dripGrade(), expected.get(click - 1), VALVE_GRADE + click);
            }
        }
        helper.succeed();
    }

    /**
     * A tap holding a blaze canister over a crucible moves its drips into the
     * crucible's reservoir: the reservoir rises by the mB the canister lost
     * (decision landing-goo-enters-any-holder).
     *
     * @param helper the gametest helper
     */
    public static void tapDripFillsCrucibleBelow(GameTestHelper helper) {
        CrucibleBlockEntity crucible = crucibleBelowTap(helper);
        TapBlockEntity tap = blazeTap(helper, START_VOLUME, TEST_GRADE);
        BlockPos tapAbs = helper.absolutePos(TAP_POS);

        helper.succeedWhen(() -> {
            int lost = START_VOLUME - tap.getFluidContent().amount();
            helper.assertTrue(lost >= DRIPS, CRUCIBLE_DRIPS_DRAWN);
            helper.assertValueEqual(TapDripScheduler.pending().stream()
                    .filter(drip -> drip.tapPos().equals(tapAbs)).count(), 0L, CRUCIBLE_PENDING);
            helper.assertValueEqual(crucible.getReservoir().getVolume(GooTypes.BLAZE), lost, CRUCIBLE_RESERVOIR);
        });
    }

    /**
     * A tap at 1:4 over a crucible moves 4 mB a tick: a canister of 40 mB
     * empties into the reservoir in 10 ticks, where 1:1 would have moved 10
     * (decision five-rates-in-fourfold-steps).
     *
     * @param helper the gametest helper
     */
    public static void tapDripOneToFourFillsCrucible(GameTestHelper helper) {
        CrucibleBlockEntity crucible = crucibleBelowTap(helper);
        TapBlockEntity tap = blazeTap(helper, ONE_TO_FOUR_MOVED, TapDripGrade.FOUR_PER_TICK);

        helper.runAfterDelay(ONE_TO_FOUR_TICKS + SETTLE_TICKS, () -> {
            helper.assertValueEqual(tap.getFluidContent().amount(), 0, ONE_TO_FOUR_CANISTER);
            helper.assertValueEqual(crucible.getReservoir().getVolume(GooTypes.BLAZE), ONE_TO_FOUR_MOVED,
                    CRUCIBLE_RESERVOIR);
            helper.succeed();
        });
    }

    /**
     * A drip landing on a crucible pours into its reservoir and runs no tap
     * program (decision landing-goo-enters-any-holder).
     *
     * @param helper the gametest helper
     */
    public static void tapDripIntoCrucibleRunsNoProgram(GameTestHelper helper) {
        BlockPos cruciblePos = TAP_POS.below();
        helper.setBlock(cruciblePos, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(cruciblePos, CrucibleBlockEntity.class);
        AtomicInteger abilityRuns = new AtomicInteger();

        int programs = landAt(helper, cruciblePos, GooTypes.BLAZE, abilityRuns);

        helper.assertValueEqual(programs, 0, RECEPTACLE_PROGRAMS);
        helper.assertValueEqual(abilityRuns.get(), 0, RECEPTACLE_ABILITY_RUNS);
        helper.assertValueEqual(crucible.getReservoir().getVolume(GooTypes.BLAZE), 1, CRUCIBLE_RESERVOIR);
        helper.succeed();
    }

    /**
     * A drip landing on a block that keeps none of it, stone or a canister
     * holding another type, runs the type's tap program and inserts nothing
     * (decision landing-goo-enters-any-holder).
     *
     * @param helper the gametest helper
     */
    public static void tapDripOnRefusingBlockRunsProgram(GameTestHelper helper) {
        BlockPos stonePos = TAP_POS.below();
        helper.setBlock(stonePos, Blocks.STONE);
        BlockPos canisterPos = OTHER_TYPE_CANISTER_POS;
        helper.setBlock(canisterPos, GooBlocks.CANISTER.get());
        CanisterBlockEntity canister = helper.getBlockEntity(canisterPos, CanisterBlockEntity.class);
        canister.insertCanister(NEIGHBOR_SLOT, new ItemStack(GooItems.CANISTER.get()), false);
        canister.insertGoo(NEIGHBOR_SLOT, TYPE, START_VOLUME);

        for (BlockPos landing : List.of(stonePos, canisterPos)) {
            AtomicInteger abilityRuns = new AtomicInteger();
            int programs = landAt(helper, landing, GooTypes.BLAZE, abilityRuns);
            helper.assertValueEqual(programs, 1, REFUSED_PROGRAMS);
            helper.assertValueEqual(abilityRuns.get(), 1, REFUSED_ABILITY_RUNS);
        }
        helper.assertValueEqual(canister.getSlotFluidContent(NEIGHBOR_SLOT).amount(), START_VOLUME, NEIGHBOR_VOLUME);
        helper.assertValueEqual(canister.getSlotGooType(NEIGHBOR_SLOT), TYPE, REFUSED_CANISTER_TYPE);
        helper.succeed();
    }

    /**
     * Lands one drip on a block through the scheduler's landing, with a tap
     * ability that counts its runs and answers one program.
     *
     * @param helper      the gametest helper
     * @param landing     the block the drip lands on, relative
     * @param type        the goo type the drip carries
     * @param abilityRuns counts the tap ability's runs
     * @return the programs the landing answered
     */
    private static int landAt(GameTestHelper helper, BlockPos landing, ResourceKey<GooTypeDefinition> type,
                              AtomicInteger abilityRuns) {
        BlockPos landingAbs = helper.absolutePos(landing);
        TapDripScheduler.PendingDrip drip = new TapDripScheduler.PendingDrip(helper.getLevel(),
                landingAbs.above(), landingAbs, Direction.UP, type, 1, 0);
        return TapDripScheduler.land(drip, TapDripScheduler.receptacleAt(helper.getLevel(), landingAbs),
                arrived -> abilityRuns.incrementAndGet());
    }

    /**
     * A sneaking empty-hand click on the valve of a gasketed tap steps the
     * grade back one, from 1:4 down through 64:1 to off, and off stays off,
     * the gasket installed after every click; a sneaking click on the body
     * still pops the gasket (decision shift-click-steps-valve-back).
     *
     * @param helper the gametest helper
     */
    public static void tapSneakClickStepsValveBack(GameTestHelper helper) {
        helper.setBlock(TAP_POS, GooBlocks.TAP.get().defaultBlockState().setValue(TapBlock.OPEN, true));
        TapBlockEntity tap = helper.getBlockEntity(TAP_POS, TapBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult bodyHit = tapHit(helper, BODY_HIT_PX);
        installGasketThenSneak(helper, tap, player, bodyHit);
        tap.setDripGrade(TapDripGrade.FOUR_PER_TICK);
        List<TapDripGrade> expected = List.of(TapDripGrade.ONE_PER_TICK, TapDripGrade.ONE_PER_4_TICKS,
                TapDripGrade.ONE_PER_16_TICKS, TapDripGrade.ONE_PER_64_TICKS);

        for (int click = 1; click <= VALVE_CLICKS; click++) {
            helper.useBlock(TAP_POS, player, tapHit(helper, VALVE_HIT_PX));
            boolean expectOpen = click <= expected.size();
            helper.assertValueEqual(helper.getBlockState(TAP_POS).getValue(TapBlock.OPEN), expectOpen,
                    VALVE_OPEN + click);
            if (expectOpen) {
                helper.assertValueEqual(tap.dripGrade(), expected.get(click - 1), VALVE_GRADE + click);
            }
            helper.assertTrue(tap.getGasketId(GasketRole.RECEIVER) != null, GASKET_KEPT + click);
        }

        helper.useBlock(TAP_POS, player, bodyHit);
        helper.assertTrue(tap.getGasketId(GasketRole.RECEIVER) == null, GASKET_POPPED);
        helper.succeed();
    }

    /**
     * Slots a canister, installs the tap's gasket with a body click, then
     * leaves the player sneaking empty-handed on the last hotbar slot, so a
     * popped gasket lands in the inventory and the hand stays empty.
     *
     * @param helper  the gametest helper
     * @param tap     the tap
     * @param player  the clicking player
     * @param bodyHit a hit on the tap's body
     */
    private static void installGasketThenSneak(GameTestHelper helper, TapBlockEntity tap, Player player,
                                               BlockHitResult bodyHit) {
        tap.insertCanister(new ItemStack(GooItems.CANISTER.get()));
        player.getInventory().setSelectedSlot(Inventory.getSelectionSize() - 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CHORAL_GASKET.get()));
        helper.useBlock(TAP_POS, player, bodyHit);
        helper.assertTrue(tap.getGasketId(GasketRole.RECEIVER) != null, GASKET_INSTALLED);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
    }

    /**
     * @param helper  the gametest helper
     * @param localPx the hit point inside the tap's block, in pixels
     * @return a hit on the tap's top face at that point
     */
    private static BlockHitResult tapHit(GameTestHelper helper, Vec3 localPx) {
        BlockPos tapAbs = helper.absolutePos(TAP_POS);
        return new BlockHitResult(Vec3.atLowerCornerOf(tapAbs).add(localPx.scale(1.0 / PIXELS_PER_BLOCK)),
                Direction.UP, tapAbs, false);
    }

    /**
     * @param helper the gametest helper
     * @return a crucible placed straight under the tap's position
     */
    private static CrucibleBlockEntity crucibleBelowTap(GameTestHelper helper) {
        helper.setBlock(TAP_POS.below(), GooBlocks.CRUCIBLE.get());
        return helper.getBlockEntity(TAP_POS.below(), CrucibleBlockEntity.class);
    }

    /**
     * Places an open tap whose canister holds blaze, dripping at a grade.
     *
     * @param helper the gametest helper
     * @param volume the blaze the canister holds, in mB
     * @param grade  the grade the tap drips at
     * @return the tap
     */
    private static TapBlockEntity blazeTap(GameTestHelper helper, int volume, TapDripGrade grade) {
        helper.setBlock(TAP_POS, GooBlocks.TAP.get().defaultBlockState().setValue(TapBlock.OPEN, true));
        TapBlockEntity tap = helper.getBlockEntity(TAP_POS, TapBlockEntity.class);
        tap.insertCanister(new ItemStack(GooItems.CANISTER.get()));
        tap.insertGoo(GooTypes.BLAZE, volume);
        tap.setDripGrade(grade);
        return tap;
    }

    private static TapBlockEntity filledTap(GameTestHelper helper) {
        return filledTap(helper, true);
    }

    private static TapBlockEntity filledTap(GameTestHelper helper, boolean open) {
        return filledTap(helper, TAP_POS, open, 0);
    }

    /**
     * Places a tap over stone with an air gap between, and fills its canister.
     *
     * @param helper the gametest helper
     * @param tapPos where the tap stands
     * @param open   whether the valve starts open
     * @param airGap air blocks between the tap and the stone
     * @return the tap
     */
    private static TapBlockEntity filledTap(GameTestHelper helper, BlockPos tapPos, boolean open, int airGap) {
        for (int gap = 1; gap <= airGap; gap++) {
            helper.setBlock(tapPos.below(gap), Blocks.AIR);
        }
        helper.setBlock(tapPos.below(airGap + 1), Blocks.STONE);
        helper.setBlock(tapPos, GooBlocks.TAP.get().defaultBlockState().setValue(TapBlock.OPEN, open));
        TapBlockEntity tap = helper.getBlockEntity(tapPos, TapBlockEntity.class);
        tap.insertCanister(new ItemStack(GooItems.CANISTER.get()));
        tap.insertGoo(TYPE, START_VOLUME);
        tap.setDripGrade(TEST_GRADE);
        return tap;
    }
}
