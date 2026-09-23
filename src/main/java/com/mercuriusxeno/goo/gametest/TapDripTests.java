package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.PlaceBlockStep;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.TapHost;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapDripScheduler;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gametests for the tap's drip: what it draws, from where, and where it lands.
 */
public final class TapDripTests {

    private static final BlockPos TAP_POS = new BlockPos(1, 1, 1);
    private static final ResourceKey<GooTypeDefinition> TYPE = GooTypes.ROCK;
    private static final int START_VOLUME = 1000;
    private static final int DRIPS = 3;
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

    private TapDripTests() {
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

        helper.runAfterDelay(DRIPS * TapBlockEntity.DRIP_INTERVAL + SETTLE_TICKS, () -> {
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
        int closedTicks = DRIPS * TapBlockEntity.DRIP_INTERVAL + SETTLE_TICKS;

        helper.runAfterDelay(closedTicks, () -> {
            helper.assertValueEqual(tap.getFluidContent().amount(), START_VOLUME, CLOSED_VOLUME);
            helper.setBlock(TAP_POS, helper.getBlockState(TAP_POS).setValue(TapBlock.OPEN, true));
        });
        helper.runAfterDelay(closedTicks + TapBlockEntity.DRIP_INTERVAL + SETTLE_TICKS, () -> {
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

        helper.runAfterDelay(DRIPS * TapBlockEntity.DRIP_INTERVAL + SETTLE_TICKS, () -> {
            helper.assertValueEqual(tap.getFluidContent().amount(), START_VOLUME, BOTTOMLESS_VOLUME);
            helper.assertValueEqual(TapDripScheduler.pending().stream()
                    .filter(drip -> drip.tapPos().equals(tapAbs)).count(), 0L, BOTTOMLESS_PENDING);
            helper.succeed();
        });
    }

    /**
     * A place-block program on the tap host at a landing writes the block
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
        List<Step> placeGlass = List.of(new PlaceBlockStep(GLASS, Map.of()));

        for (BlockPos landing : List.of(openLanding, coveredLanding)) {
            ProgramBehavior.forHost(placeGlass, HostKind.TAP)
                    .tick(new TapHost(helper.getLevel(), helper.absolutePos(landing), Direction.UP, TYPE));
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

        helper.runAfterDelay(TapBlockEntity.DRIP_INTERVAL + SETTLE_TICKS, () -> {
            helper.assertValueEqual(tap.getFluidContent().amount(), START_VOLUME - 1, TAP_VOLUME);
            helper.assertValueEqual(TapDripScheduler.pending().stream()
                    .filter(drip -> drip.tapPos().equals(tapAbs)).count(), 0L, NO_ABILITY_PENDING);
            before.forEach((pos, state) -> helper.assertValueEqual(helper.getBlockState(pos), state, NEIGHBOR_STATE));
            helper.assertValueEqual(helper.getLevel().getEntities((Entity) null, around, entity -> true).size(), 0,
                    NO_ABILITY_ENTITIES);
            helper.succeed();
        });
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
        return tap;
    }
}
