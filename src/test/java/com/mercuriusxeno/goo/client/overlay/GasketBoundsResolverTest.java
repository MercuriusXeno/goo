package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.function.Supplier;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Every gasket holder the tuner can aim at answers overlay bounds for a gasket it
 * carries (decision diagnose-then-fix-overlay-and-scan).
 */
class GasketBoundsResolverTest {

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

    static Stream<Arguments> gasketedHolders() {
        return Stream.of(
                Arguments.of("canister", (Supplier<BlockEntity>) () -> slotted(CanisterBlockEntity.class),
                        GasketRole.RECEIVER, 0),
                Arguments.of("hub", (Supplier<BlockEntity>) () -> slotted(HubBlockEntity.class),
                        GasketRole.TRANSMITTER, 0),
                Arguments.of("reactor output", (Supplier<BlockEntity>) GasketBoundsResolverTest::reactor,
                        GasketRole.TRANSMITTER, ReactorBlockEntity.OUTPUT_SLOT),
                Arguments.of("tap", (Supplier<BlockEntity>) GasketBoundsResolverTest::tap,
                        GasketRole.RECEIVER, -1),
                Arguments.of("vat", (Supplier<BlockEntity>) GasketBoundsResolverTest::vat,
                        GasketRole.RECEIVER, -1),
                Arguments.of("crucible", (Supplier<BlockEntity>) GasketBoundsResolverTest::crucible,
                        GasketRole.TRANSMITTER, -1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gasketedHolders")
    void resolveGasketBounds(String holder, Supplier<BlockEntity> entity, GasketRole role, int slot) {
        assertNotNull(GasketBoundsResolver.resolveGasketBounds(entity.get(), role, slot), holder);
    }

    private static <T extends BlockEntity> T slotted(Class<T> type) {
        T be = mock(type);
        ItemStack canister = mock(ItemStack.class);
        when(canister.isEmpty()).thenReturn(false);
        if (be instanceof CanisterBlockEntity cbe) {
            when(cbe.getCanister(0)).thenReturn(canister);
        } else if (be instanceof HubBlockEntity hbe) {
            when(hbe.getCanister(0)).thenReturn(canister);
        }
        return be;
    }

    private static BlockEntity reactor() {
        ReactorBlockEntity be = mock(ReactorBlockEntity.class);
        ItemStack canister = mock(ItemStack.class);
        when(canister.isEmpty()).thenReturn(false);
        when(be.getCanister(ReactorBlockEntity.OUTPUT_SLOT)).thenReturn(canister);
        BlockState state = mock(BlockState.class);
        when(state.getValue(ReactorBlock.FACING)).thenReturn(Direction.SOUTH);
        when(be.getBlockState()).thenReturn(state);
        return be;
    }

    private static BlockEntity tap() {
        TapBlockEntity be = mock(TapBlockEntity.class);
        BlockState state = mock(BlockState.class);
        when(state.getValue(TapBlock.FACING)).thenReturn(Direction.SOUTH);
        when(state.getValue(TapBlock.HAS_GASKET)).thenReturn(true);
        when(be.getBlockState()).thenReturn(state);
        return be;
    }

    private static BlockEntity vat() {
        VatBlockEntity be = mock(VatBlockEntity.class);
        BlockState state = mock(BlockState.class);
        when(state.getValue(VatBlock.GASKET_CAP)).thenReturn(true);
        when(be.getBlockState()).thenReturn(state);
        return be;
    }

    private static BlockEntity crucible() {
        CrucibleBlockEntity be = mock(CrucibleBlockEntity.class);
        BlockState state = mock(BlockState.class);
        when(state.getValue(CrucibleBlock.HAS_GASKET)).thenReturn(true);
        when(be.getBlockState()).thenReturn(state);
        return be;
    }
}
