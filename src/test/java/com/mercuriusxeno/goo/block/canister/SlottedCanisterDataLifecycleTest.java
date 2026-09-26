package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.block.GooMachineBlockEntity;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.block.gasket.SlotGasketPusher;
import com.mercuriusxeno.goo.block.gasket.SlotGasketRegistration;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import java.util.function.Supplier;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Every slotted host, whatever its slot shape, puts a canister in and takes it out through
 * the one lifecycle SlottedCanisterData owns, each step running once (decision
 * machine-base-owns-the-lifecycle). The slot, the owner and the gasket statics are mocks, so
 * the lifecycle's own sequencing is all that runs.
 */
class SlottedCanisterDataLifecycleTest {

    private static final BlockPos POS = new BlockPos(1, 2, 3);

    private GooMachineBlockEntity owner;
    private ServerLevel level;
    private Runnable sync;
    private Supplier<GasketRegistry> access;
    private MockedStatic<SlotGasketRegistration> registration;
    private MockedStatic<SlotGasketPusher> pusher;
    private MockedStatic<CanisterItem> canisterItem;

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
     * The canister block's grid, the hub's ring, the reactor's output hollow and the tap's body slot.
     *
     * @return host name, slot count and the slot the theory drives
     */
    static Stream<Arguments> hosts() {
        return Stream.of(
                Arguments.of("canister", CanisterBlockEntity.MAX_SLOTS, CanisterBlock.CENTER_SLOT),
                Arguments.of("hub", 8, 3),
                Arguments.of("reactor", 1, 0),
                Arguments.of("tap", 1, 0));
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void standOwner() {
        owner = mock(GooMachineBlockEntity.class);
        level = mock(ServerLevel.class);
        sync = mock(Runnable.class);
        access = mock(Supplier.class);
        GasketAttachment attachment = mock(GasketAttachment.class);
        when(attachment.registryAccess()).thenReturn(access);
        doReturn(attachment).when(owner).gasket();
        doReturn(sync).when(owner).gasketSyncCallback();
        doReturn(level).when(owner).getLevel();
        doReturn(POS).when(owner).getBlockPos();
        registration = mockStatic(SlotGasketRegistration.class);
        pusher = mockStatic(SlotGasketPusher.class);
        canisterItem = mockStatic(CanisterItem.class);
        canisterItem.when(() -> CanisterItem.getMetadata(any())).thenReturn(CanisterMetadata.EMPTY);
    }

    @AfterEach
    void closeStatics() {
        registration.close();
        pusher.close();
        canisterItem.close();
    }

    private SlottedCanisterData data(int slotCount, boolean slotAllowed) {
        return new SlottedCanisterData(owner, slotCount, i -> Shapes.empty(), s -> Shapes.empty(),
                index -> slotAllowed);
    }

    private static CanisterSlot mockSlot(SlottedCanisterData data, int index, boolean empty) {
        CanisterSlot slot = mock(CanisterSlot.class);
        when(slot.isEmpty()).thenReturn(empty);
        when(slot.index()).thenReturn(index);
        data.slots[index] = slot;
        return slot;
    }

    private static ItemStack canisterStack() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(mock(CanisterItem.class));
        when(stack.copyWithCount(1)).thenReturn(stack);
        return stack;
    }

    @Nested
    class Insert {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.mercuriusxeno.goo.block.canister.SlottedCanisterDataLifecycleTest#hosts")
        void runsEveryStepOnce(String host, int slotCount, int index) {
            SlottedCanisterData data = data(slotCount, true);
            CanisterSlot slot = mockSlot(data, index, true);
            ItemStack stack = canisterStack();

            assertTrue(data.insert(index, stack, true), host);

            InOrder order = inOrder(slot, level, sync);
            order.verify(slot).setCanister(stack);
            order.verify(slot).stripGaskets();
            order.verify(slot).buildHandler(any());
            pusher.verify(() -> SlotGasketPusher.rebuild(slot, owner, access), times(1));
            order.verify(level).invalidateCapabilities(POS);
            registration.verify(() -> SlotGasketRegistration.register(
                    access, level, POS, index, CanisterMetadata.EMPTY), times(1));
            order.verify(sync).run();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.mercuriusxeno.goo.block.canister.SlottedCanisterDataLifecycleTest#hosts")
        void keepsGasketsWhenNotStripping(String host, int slotCount, int index) {
            SlottedCanisterData data = data(slotCount, true);
            CanisterSlot slot = mockSlot(data, index, true);

            data.insert(index, canisterStack(), false);

            verify(slot, never()).stripGaskets();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.mercuriusxeno.goo.block.canister.SlottedCanisterDataLifecycleTest#hosts")
        void closedSlotTakesNothing(String host, int slotCount, int index) {
            SlottedCanisterData data = data(slotCount, false);
            CanisterSlot slot = mockSlot(data, index, true);

            assertFalse(data.insert(index, canisterStack(), false), host);

            verify(slot, never()).setCanister(any());
            verifyNoInteractions(sync);
            registration.verifyNoInteractions();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.mercuriusxeno.goo.block.canister.SlottedCanisterDataLifecycleTest#hosts")
        void occupiedSlotTakesNothing(String host, int slotCount, int index) {
            SlottedCanisterData data = data(slotCount, true);
            CanisterSlot slot = mockSlot(data, index, false);

            assertFalse(data.insert(index, canisterStack(), false), host);

            verify(slot, never()).setCanister(any());
        }
    }

    @Nested
    class Remove {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.mercuriusxeno.goo.block.canister.SlottedCanisterDataLifecycleTest#hosts")
        void runsEveryStepOnce(String host, int slotCount, int index) {
            SlottedCanisterData data = data(slotCount, true);
            CanisterSlot slot = mockSlot(data, index, false);
            ItemStack held = mock(ItemStack.class);
            ItemStack copy = mock(ItemStack.class);
            when(slot.canister()).thenReturn(held);
            when(held.copy()).thenReturn(copy);

            assertSame(copy, data.remove(index), host);

            InOrder order = inOrder(slot, level);
            order.verify(slot).disposePusher();
            order.verify(slot).syncHandlerToStack();
            order.verify(slot).clear();
            order.verify(level).invalidateCapabilities(POS);
            registration.verify(() -> SlotGasketRegistration.deregister(access, CanisterMetadata.EMPTY),
                    times(1));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.mercuriusxeno.goo.block.canister.SlottedCanisterDataLifecycleTest#hosts")
        void emptySlotGivesNothing(String host, int slotCount, int index) {
            SlottedCanisterData data = data(slotCount, true);
            CanisterSlot slot = mockSlot(data, index, true);

            assertTrue(data.remove(index).isEmpty(), host);

            verify(slot, never()).clear();
            registration.verifyNoInteractions();
        }
    }
}
