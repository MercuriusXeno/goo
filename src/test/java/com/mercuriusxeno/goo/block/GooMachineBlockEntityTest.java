package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.SlottedCanisterData;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.gasket.ChoralGasketBlockEntity;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Every machine block entity runs the lifecycle the base owns, each hook reaching the
 * gasket attachment or the machine's held slots exactly once (decision
 * machine-base-owns-the-lifecycle). Each machine is a real-method spy whose attachment
 * and held slots are mocks, so the base's forwarding is all that runs.
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

    static Stream<Class<? extends GooMachineBlockEntity>> machines() {
        return Stream.of(CanisterBlockEntity.class, HubBlockEntity.class, VatBlockEntity.class,
                CrucibleBlockEntity.class, ReactorBlockEntity.class, TapBlockEntity.class,
                ChoralGasketBlockEntity.class);
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

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.block.GooMachineBlockEntityTest#machines")
        void clientLevelReachesAttachmentOnceAndRegistersNoSlotGasket(Class<? extends GooMachineBlockEntity> type) {
            GasketAttachment attachment = mock(GasketAttachment.class);
            SlottedCanisterData slots = mock(SlottedCanisterData.class);
            GooMachineBlockEntity be = spyMachine(type, attachment, slots);
            Level level = mock(Level.class);

            be.setLevel(level);

            verify(attachment, times(1)).onSetLevel(level);
            verify(slots, never()).registerSlotGaskets();
        }

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.block.GooMachineBlockEntityTest#machines")
        void serverLevelReachesAttachmentOnceAndRegistersSlotGasketsOnce(
                Class<? extends GooMachineBlockEntity> type) {
            GasketAttachment attachment = mock(GasketAttachment.class);
            SlottedCanisterData slots = mock(SlottedCanisterData.class);
            GooMachineBlockEntity be = spyMachine(type, attachment, slots);
            ServerLevel level = mock(ServerLevel.class);

            be.setLevel(level);

            verify(attachment, times(1)).onSetLevel(level);
            verify(slots, times(1)).registerSlotGaskets();
        }
    }

    @Nested
    class OnLoad {

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.block.GooMachineBlockEntityTest#machines")
        void reachesAttachmentOnce(Class<? extends GooMachineBlockEntity> type) {
            GasketAttachment attachment = mock(GasketAttachment.class);
            SlottedCanisterData slots = mock(SlottedCanisterData.class);
            GooMachineBlockEntity be = spyMachine(type, attachment, slots);

            be.onLoad();

            verify(attachment, times(1)).onLoad();
        }
    }

    @Nested
    class SetRemoved {

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.block.GooMachineBlockEntityTest#machines")
        void releasesSlotGasketsOnce(Class<? extends GooMachineBlockEntity> type) {
            SlottedCanisterData slots = mock(SlottedCanisterData.class);
            GooMachineBlockEntity be = spyMachine(type, mock(GasketAttachment.class), slots);

            be.setRemoved();

            verify(slots, times(1)).releaseSlotGaskets();
        }
    }

    @Nested
    class UpdateTag {

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.block.GooMachineBlockEntityTest#machines")
        void carriesTheFullMetadataSave(Class<? extends GooMachineBlockEntity> type) {
            SlottedCanisterData slots = mock(SlottedCanisterData.class);
            GooMachineBlockEntity be = spyMachine(type, mock(GasketAttachment.class), slots);
            HolderLookup.Provider registries = mock(HolderLookup.Provider.class);
            CompoundTag saved = new CompoundTag();
            doReturn(saved).when(be).saveWithFullMetadata(registries);

            assertSame(saved, be.getUpdateTag(registries));
        }
    }
}
