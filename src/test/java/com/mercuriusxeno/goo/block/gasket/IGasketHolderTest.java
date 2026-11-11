package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Each machine's gasket roles read the gasket flags its blockstate records. Each machine is a real-method
 * mock standing in a mocked blockstate, so no block registers.
 */
class IGasketHolderTest {

    private static <T extends BlockEntity> T standingIn(Class<T> type, Map<BooleanProperty, Boolean> flags) {
        BlockState state = mock(BlockState.class);
        flags.forEach((flag, value) -> when(state.getValue(flag)).thenReturn(value));
        T machine = mock(type, CALLS_REAL_METHODS);
        doReturn(state).when(machine).getBlockState();
        return machine;
    }

    @Nested
    class Crucible {

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void supportsTheTransmitterItsGasketFlagRecords(boolean hasGasket) {
            CrucibleBlockEntity crucible = standingIn(CrucibleBlockEntity.class, Map.of(CrucibleBlock.HAS_GASKET, hasGasket));

            assertEquals(hasGasket, crucible.supportsRole(GasketRole.TRANSMITTER));
        }
    }

    @Nested
    class Vat {

        @Test
        void supportsTheReceiverOnItsCapAndTheTransmitterOnItsBase() {
            VatBlockEntity capped = standingIn(VatBlockEntity.class,
                    Map.of(VatBlock.GASKET_CAP, true, VatBlock.GASKET_BASE, false));
            VatBlockEntity based = standingIn(VatBlockEntity.class,
                    Map.of(VatBlock.GASKET_CAP, false, VatBlock.GASKET_BASE, true));

            assertEquals(List.of(true, false),
                    List.of(capped.supportsRole(GasketRole.RECEIVER), capped.supportsRole(GasketRole.TRANSMITTER)));
            assertEquals(List.of(false, true),
                    List.of(based.supportsRole(GasketRole.RECEIVER), based.supportsRole(GasketRole.TRANSMITTER)));
        }
    }

    // decision diagnose-then-fix-hub-canister-transmitter
    @Nested
    class Hub {

        @Test
        void supportsBothRolesForItsSlotCanisters() {
            HubBlockEntity hub = standingIn(HubBlockEntity.class, Map.of(HubBlock.HAS_GASKET, false));

            assertEquals(List.of(true, true),
                    List.of(hub.supportsRole(GasketRole.TRANSMITTER), hub.supportsRole(GasketRole.RECEIVER)));
        }

        @Test
        void itsIntakeIsTheReceiverItsGasketFlagRecords() {
            HubBlockEntity hub = standingIn(HubBlockEntity.class, Map.of(HubBlock.HAS_GASKET, true));

            assertEquals(HubBlock.HAS_GASKET, hub.gasketFlag(GasketRole.RECEIVER));
            assertEquals(true, hub.holdsBlockGasket(GasketRole.RECEIVER));
        }
    }
}
