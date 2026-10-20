package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.item.GooLabelRemoval.Removal;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A goo label comes off a canister as one label of its type, the canister keeping
 * its text label, gaskets and partners (decision label-comes-off-in-the-crafting-grid).
 */
class GooLabelRemovalTest {

    private static final String CORNER = "Corner";

    @Test
    void aLabeledCanisterGivesUpItsLabelAndKeepsTheRest() {
        CanisterMetadata unlabeled = CanisterMetadata.EMPTY.withLabel(CORNER)
                .withTopGasketId(UUID.randomUUID())
                .withTopPartner(new GasketPartner(new BlockPos(1, 2, 3), 0));

        Optional<Removal> removal = GooLabelRemoval.off(unlabeled.withGooLabel(GooTypes.BLAZE));

        assertEquals(Optional.of(new Removal(GooTypes.BLAZE, unlabeled)), removal);
    }

    @Test
    void anUnlabeledCanisterGivesUpNothing() {
        assertEquals(Optional.empty(), GooLabelRemoval.off(CanisterMetadata.EMPTY.withLabel(CORNER)));
    }
}
