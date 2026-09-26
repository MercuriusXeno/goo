package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.HudAnchor;
import com.mercuriusxeno.goo.block.canister.HudViewer;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import com.mercuriusxeno.goo.block.canister.SlottedCanisterData;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.gasket.ChoralGasketBlockEntity;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Every canister and gasket holder answers the bounds the client overlays and
 * HUD draw through ICanisterHolder and IGasketHolder (decision
 * hosts-answer-bounds-through-interfaces). Each holder is a real-method spy
 * whose block state, position and held canisters are stubbed.
 */
class HolderBoundsTest {

    private static final BlockPos ORIGIN = BlockPos.ZERO;
    private static final HudViewer VIEWER = new HudViewer(Direction.NORTH, Direction.SOUTH, false);

    /**
     * A block entity class initializes only past NeoForge's AttachmentHolder, which asks FML
     * whether it runs in production, and past the vanilla registries; a test JVM holds neither,
     * so a stubbed FML loader answers the one and the vanilla bootstrap stands the other.
     *
     * @throws ClassNotFoundException never, the class is on the test classpath
     */
    @BeforeAll
    static void initializeBlockEntitySupertypes() throws ClassNotFoundException {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            Class.forName(AttachmentHolder.class.getName(), true, AttachmentHolder.class.getClassLoader());
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    /**
     * A holder under test: how to stand it with every slot filled or empty and every
     * gasket flag raised or lowered, the hit that aims at its slot, and that slot.
     *
     * @param name            the holder's name in the report
     * @param stand           builds the holder from (slots filled, gasket flag raised)
     * @param hit             a hit on the holder's slot, or its body for a slotless holder
     * @param slot            the slot the hit addresses, or -1 for a block-level gasket
     * @param role            the gasket role the overlay reads
     * @param anchorsWhenEmpty true when the holder anchors the HUD on an empty slot too
     */
    record Holder(String name, BiFunction<Boolean, Boolean, IGasketHolder> stand,
                  BlockHitResult hit, int slot, GasketRole role, boolean anchorsWhenEmpty) {
        IGasketHolder stood(boolean filled, boolean gasketed) {
            return stand.apply(filled, gasketed);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Holder> canisterHolders() {
        return Stream.of(
                new Holder("canister", (filled, gasketed) -> canister(filled),
                        hitAt(0.5, 0.75, 0.5, Direction.UP), 4, GasketRole.RECEIVER, true),
                new Holder("hub", (filled, gasketed) -> hub(filled),
                        hitAt(0.5, 0.5, 2.0 / 16.0, Direction.NORTH), 0, GasketRole.TRANSMITTER, true),
                new Holder("tap", (filled, gasketed) -> tap(filled, gasketed, Direction.SOUTH),
                        hitAt(0.5, 10.0 / 16.0, 3.0 / 16.0, Direction.NORTH), -1, GasketRole.RECEIVER, false),
                new Holder("reactor", (filled, gasketed) -> reactor(filled),
                        hitAt(0.5, 7.0 / 16.0, 3.0 / 16.0, Direction.NORTH), ReactorBlockEntity.OUTPUT_SLOT,
                        GasketRole.TRANSMITTER, false));
    }

    static Stream<Holder> gasketHolders() {
        Stream<Holder> blockLevel = Stream.of(
                new Holder("vat", (filled, gasketed) -> vat(gasketed),
                        hitAt(0.5, 0.75, 0.5, Direction.UP), -1, GasketRole.RECEIVER, false),
                new Holder("crucible", (filled, gasketed) -> crucible(gasketed),
                        hitAt(0.5, 0.75, 0.5, Direction.UP), -1, GasketRole.TRANSMITTER, false));
        return Stream.concat(canisterHolders(), blockLevel);
    }

    @Nested
    class CanisterHolderMembers {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.mercuriusxeno.goo.block.HolderBoundsTest#canisterHolders")
        void filledSlotAnswersEveryShapeAndNoPreview(Holder holder) {
            ICanisterHolder be = (ICanisterHolder) holder.stood(true, true);
            int slot = Math.max(holder.slot(), 0);
            assertAll(holder.name(),
                    () -> assertNotNull(be.slotBounds(slot), "slotBounds"),
                    () -> assertNotNull(be.outlineShape(holder.hit()), "outlineShape"),
                    () -> assertNotNull(be.pickupBounds(holder.hit()), "pickupBounds"),
                    () -> assertNull(be.previewBounds(holder.hit(), false), "previewBounds"),
                    () -> assertNotNull(be.hudAnchor(holder.hit(), VIEWER), "hudAnchor"),
                    () -> assertTrue(be.takesCanisterAt(holder.hit(), false), "takesCanisterAt"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.mercuriusxeno.goo.block.HolderBoundsTest#canisterHolders")
        void emptySlotAnswersPreviewAndNoPickup(Holder holder) {
            ICanisterHolder be = (ICanisterHolder) holder.stood(false, true);
            int slot = Math.max(holder.slot(), 0);
            HudAnchor anchor = be.hudAnchor(holder.hit(), VIEWER);
            assertAll(holder.name(),
                    () -> assertNotNull(be.slotBounds(slot), "slotBounds"),
                    () -> assertNotNull(be.outlineShape(holder.hit()), "outlineShape"),
                    () -> assertNull(be.pickupBounds(holder.hit()), "pickupBounds"),
                    () -> assertNotNull(be.previewBounds(holder.hit(), false), "previewBounds"),
                    () -> assertEquals(holder.anchorsWhenEmpty(), anchor != null, "hudAnchor"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.mercuriusxeno.goo.block.HolderBoundsTest#canisterHolders")
        void slotPastTheHolderAnswersNoBounds(Holder holder) {
            ICanisterHolder be = (ICanisterHolder) holder.stood(true, true);
            assertAll(holder.name(),
                    () -> assertNull(be.slotBounds(-1)),
                    () -> assertNull(be.slotBounds(HubBlock.SLOT_COUNT + 1)));
        }

        /**
         * The panel sits over the canister slot shape the tap's outline and preview draw.
         */
        @ParameterizedTest
        @EnumSource(value = Direction.class, names = {"SOUTH", "NORTH", "EAST", "WEST"})
        void tapAnchorsOverTheCanisterTopForEachFacing(Direction facing) {
            double[] expected = switch (facing) {
                case NORTH -> new double[] {8.0 / 16.0, 13.0 / 16.0};
                case EAST -> new double[] {3.0 / 16.0, 8.0 / 16.0};
                case WEST -> new double[] {13.0 / 16.0, 8.0 / 16.0};
                default -> new double[] {8.0 / 16.0, 3.0 / 16.0};
            };
            HudAnchor anchor = ((ICanisterHolder) tap(true, false, facing))
                    .hudAnchor(hitAt(0.5, 0.5, 0.5, Direction.UP), VIEWER);
            assertNotNull(anchor);
            assertAll(() -> assertEquals(expected[0], anchor.x(), 1e-9),
                    () -> assertEquals(expected[1], anchor.z(), 1e-9),
                    () -> assertEquals(1.0, anchor.lift(), 1e-9),
                    () -> assertEquals(Direction.UP, anchor.face()));
        }

        @Test
        void hubFrameHitAnchorsOverTheIntake() {
            HudAnchor anchor = ((ICanisterHolder) hub(true)).hudAnchor(hitAt(0.5, 1.0, 0.5, Direction.UP), VIEWER);
            assertNotNull(anchor);
            assertAll(() -> assertEquals(-1, anchor.slot()),
                    () -> assertEquals(0.5, anchor.x(), 1e-9),
                    () -> assertEquals(0.5, anchor.z(), 1e-9));
        }

        @Test
        void reactorTakesAHollowUseOnlyStanding() {
            ICanisterHolder be = (ICanisterHolder) reactor(false);
            BlockHitResult hollow = hitAt(0.5, 7.0 / 16.0, 3.0 / 16.0, Direction.NORTH);
            assertAll(() -> assertTrue(be.takesCanisterAt(hollow, false)),
                    () -> assertFalse(be.takesCanisterAt(hollow, true)));
        }

        @Test
        void canisterInsertsFromANeighbourIntoTheNearestEmptySlot() {
            ICanisterHolder empty = (ICanisterHolder) canister(false);
            ICanisterHolder full = (ICanisterHolder) canister(true);
            Vec3 centre = new Vec3(0.5, 0.0, 0.5);
            assertAll(() -> assertEquals(4, empty.insertionSlotFrom(centre)),
                    () -> assertEquals(-1, full.insertionSlotFrom(centre)),
                    () -> assertEquals(-1, ((ICanisterHolder) hub(false)).insertionSlotFrom(centre)));
        }
    }

    @Nested
    class GasketOverlayBounds {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.mercuriusxeno.goo.block.HolderBoundsTest#gasketHolders")
        void gasketedHolderAnswersItsRegion(Holder holder) {
            assertNotNull(holder.stood(true, true).slotBoundsFor(holder.slot(), holder.role()), holder.name());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.mercuriusxeno.goo.block.HolderBoundsTest#gasketHolders")
        void holderWithoutTheGasketAnswersNone(Holder holder) {
            IGasketHolder be = holder.slot() >= 0 ? holder.stood(false, true) : holder.stood(true, false);
            assertNull(be.slotBoundsFor(holder.slot(), holder.role()), holder.name());
        }

        @Test
        void slotRegionSplitsAtMidHeightByRole() {
            IGasketHolder be = canister(true);
            AABB cap = be.slotBoundsFor(4, GasketRole.RECEIVER);
            AABB base = be.slotBoundsFor(4, GasketRole.TRANSMITTER);
            assertNotNull(cap);
            assertNotNull(base);
            assertAll(() -> assertEquals(cap.minY, base.maxY, 1e-9),
                    () -> assertTrue(cap.maxY > cap.minY),
                    () -> assertTrue(base.maxY > base.minY));
        }

        @Test
        void choralGasketAnswersNoRegion() {
            ChoralGasketBlockEntity be = mock(ChoralGasketBlockEntity.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
            assertNull(be.slotBoundsFor(-1, GasketRole.RECEIVER));
        }
    }

    // --- Fixtures ---

    private static BlockHitResult hitAt(double x, double y, double z, Direction face) {
        return new BlockHitResult(new Vec3(x, y, z), face, ORIGIN, false);
    }

    private static ItemStack canisterStack(boolean filled) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.isEmpty()).thenReturn(!filled);
        return stack;
    }

    private static <T extends GooMachineBlockEntity> T spy(Class<T> type, BlockState state) {
        T be = mock(type, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(ORIGIN).when(be).getBlockPos();
        doReturn(state).when(be).getBlockState();
        doReturn(mock(Level.class)).when(be).getLevel();
        when(state.getShape(any(), any())).thenReturn(Shapes.block());
        return be;
    }

    private static <T extends GooMachineBlockEntity & ICanisterHolder> T slotted(
            Class<T> type, BlockState state, int slotCount, boolean filled) {
        T be = spy(type, state);
        SlottedCanisterData slots = mock(SlottedCanisterData.class);
        when(slots.inRange(anyInt())).thenAnswer(call -> {
            int index = call.getArgument(0);
            return index >= 0 && index < slotCount;
        });
        doReturn(slots).when(be).containerState();
        ItemStack stack = canisterStack(filled);
        doReturn(stack).when(be).getCanister(anyInt());
        return be;
    }

    private static CanisterBlockEntity canister(boolean filled) {
        return slotted(CanisterBlockEntity.class, mock(BlockState.class), CanisterBlockEntity.MAX_SLOTS, filled);
    }

    private static HubBlockEntity hub(boolean filled) {
        return slotted(HubBlockEntity.class, mock(BlockState.class), HubBlock.SLOT_COUNT, filled);
    }

    private static TapBlockEntity tap(boolean filled, boolean gasketed, Direction facing) {
        BlockState state = mock(BlockState.class);
        when(state.getValue(TapBlock.FACING)).thenReturn(facing);
        when(state.getValue(TapBlock.HAS_GASKET)).thenReturn(gasketed);
        return slotted(TapBlockEntity.class, state, 1, filled);
    }

    private static ReactorBlockEntity reactor(boolean filled) {
        BlockState state = mock(BlockState.class);
        when(state.getValue(ReactorBlock.FACING)).thenReturn(Direction.SOUTH);
        return slotted(ReactorBlockEntity.class, state, 1, filled);
    }

    private static VatBlockEntity vat(boolean gasketed) {
        BlockState state = mock(BlockState.class);
        when(state.getValue(VatBlock.GASKET_CAP)).thenReturn(gasketed);
        when(state.getValue(VatBlock.GASKET_BASE)).thenReturn(gasketed);
        return spy(VatBlockEntity.class, state);
    }

    private static CrucibleBlockEntity crucible(boolean gasketed) {
        BlockState state = mock(BlockState.class);
        when(state.getValue(CrucibleBlock.HAS_GASKET)).thenReturn(gasketed);
        return spy(CrucibleBlockEntity.class, state);
    }
}
