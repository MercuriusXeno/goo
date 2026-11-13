package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.SlottedCanisterData;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Every machine block entity runs the lifecycle the base owns, each hook reaching the
 * gasket attachment or the machine's held slots exactly once (decision
 * machine-base-owns-the-lifecycle), and its update tag is its full metadata save. The
 * lifecycle hooks are final in {@link GooMachineBlockEntity}, so the canister, a real-method
 * spy whose attachment and held slots are mocks, stands for every machine.
 */
class GooMachineBlockEntityTest {

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

    private static GooMachineBlockEntity spyMachine(Class<? extends GooMachineBlockEntity> type,
                                                    GasketAttachment attachment, SlottedCanisterData slots) {
        GooMachineBlockEntity be = mock(type, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(attachment).when(be).gasket();
        doReturn(slots).when(be).heldSlots();
        return be;
    }

    @Nested
    class SetLevel {

        @Test
        void clientLevelReachesAttachmentOnce() {
            GasketAttachment attachment = mock(GasketAttachment.class);
            SlottedCanisterData slots = mock(SlottedCanisterData.class);
            GooMachineBlockEntity be = spyMachine(CanisterBlockEntity.class, attachment, slots);
            Level level = mock(Level.class);

            be.setLevel(level);

            verify(attachment, times(1)).onSetLevel(level);
        }

        @Test
        void serverLevelReachesAttachmentOnceAndRegistersSlotGasketsOnce() {
            GasketAttachment attachment = mock(GasketAttachment.class);
            SlottedCanisterData slots = mock(SlottedCanisterData.class);
            GooMachineBlockEntity be = spyMachine(CanisterBlockEntity.class, attachment, slots);
            ServerLevel level = mock(ServerLevel.class);

            be.setLevel(level);

            verify(attachment, times(1)).onSetLevel(level);
            verify(slots, times(1)).registerSlotGaskets();
        }
    }

    @Nested
    class OnLoad {

        @Test
        void reachesAttachmentOnce() {
            GasketAttachment attachment = mock(GasketAttachment.class);
            SlottedCanisterData slots = mock(SlottedCanisterData.class);
            GooMachineBlockEntity be = spyMachine(CanisterBlockEntity.class, attachment, slots);

            be.onLoad();

            verify(attachment, times(1)).onLoad();
        }
    }

    @Nested
    class SetRemoved {

        @Test
        void releasesSlotGasketsOnce() {
            SlottedCanisterData slots = mock(SlottedCanisterData.class);
            GooMachineBlockEntity be = spyMachine(CanisterBlockEntity.class, mock(GasketAttachment.class), slots);

            be.setRemoved();

            verify(slots, times(1)).releaseSlotGaskets();
        }

        @Test
        void releasesBlockLevelPusherOnceWhenLeaving() {
            GasketAttachment attachment = mock(GasketAttachment.class);
            GooMachineBlockEntity be = spyMachine(CanisterBlockEntity.class, attachment, mock(SlottedCanisterData.class));

            be.setRemoved();

            verify(attachment, times(1)).releasePusher();
        }

        @Test
        void releasesBlockLevelPusherOnceWhenUnloading() {
            GasketAttachment attachment = mock(GasketAttachment.class);
            GooMachineBlockEntity be = spyMachine(CanisterBlockEntity.class, attachment, mock(SlottedCanisterData.class));

            be.onChunkUnloaded();
            be.setRemoved();

            verify(attachment, times(1)).releasePusher();
        }
    }

    /**
     * The update tag is final in {@link GooSyncedBlockEntity}, so one machine stands for every one.
     */
    @Nested
    class UpdateTag {

        @Test
        void carriesTheFullMetadataSave() {
            GooMachineBlockEntity be = mock(CanisterBlockEntity.class,
                    withSettings().defaultAnswer(CALLS_REAL_METHODS));
            HolderLookup.Provider registries = mock(HolderLookup.Provider.class);
            CompoundTag saved = new CompoundTag();
            doReturn(saved).when(be).saveWithFullMetadata(registries);

            assertSame(saved, be.getUpdateTag(registries));
        }
    }
}
