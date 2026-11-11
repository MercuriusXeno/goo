package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.block.GooMachineBlockEntity;
import com.mercuriusxeno.goo.block.gasket.AddressedGasket;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.block.gasket.SlotGasketPusher;
import com.mercuriusxeno.goo.block.gasket.SlotGasketRegistration;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Every slotted host puts a canister in and takes it out through the one lifecycle
 * SlottedCanisterData owns, each step running once (decision machine-base-owns-the-lifecycle);
 * the grid reads its slot count only through its range check, so the canister block's grid
 * stands for every host. The slot, the owner and the gasket statics are mocks, so the
 * lifecycle's own sequencing is all that runs.
 */
class SlottedCanisterDataLifecycleTest {

    private static final BlockPos POS = new BlockPos(1, 2, 3);
    /** The canister block's grid; the grid reads its slot count only through its range check. */
    private static final int SLOTS = CanisterBlockEntity.MAX_SLOTS;
    private static final int INDEX = CanisterBlock.CENTER_SLOT;

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
        canisterItem.when(() -> CanisterItem.getFluidContent(any())).thenReturn(CanisterFluidContent.EMPTY);
    }

    @AfterEach
    void closeStatics() {
        registration.close();
        pusher.close();
        canisterItem.close();
    }

    private SlottedCanisterData data(int slotCount, boolean slotAllowed) {
        return new SlottedCanisterData(owner, slotCount, i -> Shapes.empty(), s -> Shapes.empty(),
                index -> slotAllowed, (index, incoming) -> true);
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

        @Test
        void runsEveryStepOnce() {
            SlottedCanisterData data = data(SLOTS, true);
            CanisterSlot slot = mockSlot(data, INDEX, true);
            ItemStack stack = canisterStack();

            assertTrue(data.insert(INDEX, stack, true));

            InOrder order = inOrder(slot, level, sync);
            order.verify(slot).setCanister(stack);
            order.verify(slot).stripGaskets();
            order.verify(slot).buildHandler(any());
            pusher.verify(() -> SlotGasketPusher.rebuild(slot, owner, access), times(1));
            order.verify(level).invalidateCapabilities(POS);
            registration.verify(() -> SlotGasketRegistration.register(
                    access, level, POS, INDEX, CanisterMetadata.EMPTY), times(1));
            order.verify(sync).run();
        }

        @Test
        void closedSlotRefusesTheCanister() {
            SlottedCanisterData data = data(SLOTS, false);
            mockSlot(data, INDEX, true);

            assertFalse(data.insert(INDEX, canisterStack(), false));
        }

        @Test
        void occupiedSlotRefusesTheCanister() {
            SlottedCanisterData data = data(SLOTS, true);
            mockSlot(data, INDEX, false);

            assertFalse(data.insert(INDEX, canisterStack(), false));
        }
    }

    @Nested
    class Remove {

        @Test
        void runsEveryStepOnce() {
            SlottedCanisterData data = data(SLOTS, true);
            CanisterSlot slot = mockSlot(data, INDEX, false);
            ItemStack held = mock(ItemStack.class);
            ItemStack copy = mock(ItemStack.class);
            when(slot.canister()).thenReturn(held);
            when(held.copy()).thenReturn(copy);

            assertSame(copy, data.remove(INDEX));

            InOrder order = inOrder(slot, level);
            order.verify(slot).disposePusher();
            order.verify(slot).syncHandlerToStack();
            order.verify(slot).clear();
            order.verify(level).invalidateCapabilities(POS);
            registration.verify(() -> SlotGasketRegistration.deregister(access, CanisterMetadata.EMPTY),
                    times(1));
        }

        @Test
        void emptySlotGivesAnEmptyStack() {
            SlottedCanisterData data = data(SLOTS, true);
            mockSlot(data, INDEX, true);

            assertTrue(data.remove(INDEX).isEmpty());
        }
    }

    /**
     * Each path that changes a slot's gasket ids reaches the host's capability refresh
     * after its metadata write (decision diagnose-then-fix-capability-lifetimes): a canister
     * block entity runs its real holder defaults over this grid.
     */
    @Nested
    class MetadataWrite {

        private static final int SLOT = CanisterBlock.CENTER_SLOT;

        private CanisterBlockEntity holderOver(SlottedCanisterData data, CanisterMetadata meta) {
            CanisterBlockEntity holder = mock(CanisterBlockEntity.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
            doReturn(data).when(holder).containerState();
            doReturn(meta).when(holder).getSlotMetadata(SLOT);
            return holder;
        }

        private void verifyInvalidatedAfterWrite(CanisterSlot slot) {
            InOrder order = inOrder(slot, level);
            order.verify(slot).setMetadata(any());
            order.verify(level).invalidateCapabilities(POS);
        }

        @Test
        void aServerWriteToAFilledSlotInvalidatesTheHostAfterTheWrite() {
            SlottedCanisterData data = data(CanisterBlockEntity.MAX_SLOTS, true);
            CanisterSlot slot = mockSlot(data, SLOT, false);

            data.setMetadata(SLOT, CanisterMetadata.EMPTY.withTopGasketId(UUID.randomUUID()));

            verifyInvalidatedAfterWrite(slot);
        }

        @Test
        void mintingASlotGasketIdInvalidatesTheHost() {
            SlottedCanisterData data = data(CanisterBlockEntity.MAX_SLOTS, true);
            CanisterSlot slot = mockSlot(data, SLOT, false);

            holderOver(data, CanisterMetadata.EMPTY).ensureGasketId(GasketRole.RECEIVER, SLOT);

            verifyInvalidatedAfterWrite(slot);
        }

        @Test
        void uninstallingASlotGasketInvalidatesTheHost() {
            SlottedCanisterData data = data(CanisterBlockEntity.MAX_SLOTS, true);
            CanisterSlot slot = mockSlot(data, SLOT, false);
            CanisterMetadata installed = CanisterMetadata.EMPTY.withTopGasketId(UUID.randomUUID());

            holderOver(data, installed).uninstallGasket(new AddressedGasket(GasketRole.RECEIVER, SLOT));

            verifyInvalidatedAfterWrite(slot);
        }

        @Test
        void aClientWriteWritesTheSlot() {
            Level client = mock(Level.class);
            when(client.isClientSide()).thenReturn(true);
            doReturn(client).when(owner).getLevel();
            SlottedCanisterData data = data(CanisterBlockEntity.MAX_SLOTS, true);
            CanisterSlot slot = mockSlot(data, SLOT, false);

            data.setMetadata(SLOT, CanisterMetadata.EMPTY.withTopGasketId(UUID.randomUUID()));

            verify(slot).setMetadata(any());
        }
    }

    @Nested
    class Load {

        @Test
        void replacingEveryHandlerInvalidatesTheHost() {
            SlottedCanisterData data = data(1, true);
            CanisterSlot slot = mockSlot(data, 0, false);
            ValueInput input = mock(ValueInput.class);
            when(input.read(any(String.class), any())).thenReturn(Optional.empty());

            data.load(input, "canister");

            InOrder order = inOrder(slot, level);
            order.verify(slot).buildHandler(any());
            order.verify(level).invalidateCapabilities(POS);
        }
    }
}
