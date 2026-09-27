package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.item.CanisterItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests the vat's held-item dispatch: a canister is any other item and falls through to the
 * empty-hand unpack (decision canister-click-is-any-other-click-on-crucible-and-vat).
 */
class VatInteractionHandlerTest {

    @Test
    void canisterFallsThroughToTheEmptyHandUnpackWithoutReadingTheVat() {
        VatBlockEntity vat = mock(VatBlockEntity.class);
        ItemStack canister = mock(ItemStack.class);
        when(canister.getItem()).thenReturn(mock(CanisterItem.class));

        InteractionResult result = VatInteractionHandler.dispatchInteraction(
                vat, canister, mock(Player.class), InteractionHand.MAIN_HAND, mock(BlockHitResult.class));

        assertEquals(InteractionResult.TRY_WITH_EMPTY_HAND, result);
        verifyNoInteractions(vat);
    }
}
