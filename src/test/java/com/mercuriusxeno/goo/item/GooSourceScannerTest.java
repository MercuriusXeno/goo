package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * GooSourceScanner reads every goo-carrying item through GooCarrierItem: each of the
 * four carriers is aggregated, measured, named as a source and drawn from, and the
 * passes run in DepletionPass order (decision hosts-answer-bounds-through-interfaces).
 * Each carrier item is a real-method mock whose goo contents and draw are stubbed.
 */
class GooSourceScannerTest {

    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final int HELD = 100;

    /**
     * SharedConstants asks FML whether it runs in production, and no FML loader
     * stands in a unit test, so the loader is stubbed while vanilla bootstraps.
     */
    @BeforeAll
    static void bootstrapVanillaRegistries() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {GooItem.class, CanisterItem.class,
            VatBlockItem.class, HubBlockItem.class})
    void everyCarrierIsScanned(Class<? extends Item> type) {
        ItemStack stack = carrierStack(type);
        Player player = playerHolding(stack);
        int drawn = GooSourceScanner.deplete(player, ROCK, 60);
        assertAll(type.getSimpleName(),
                () -> assertEquals(Map.of(ROCK, HELD), GooSourceScanner.aggregateAvailable(player)),
                () -> assertEquals(HELD, GooSourceScanner.volumeIn(stack, ROCK)),
                () -> assertTrue(GooSourceScanner.hasEnough(player, ROCK, HELD)),
                () -> assertFalse(GooSourceScanner.hasEnough(player, ROCK, HELD + 1)),
                () -> assertSame(stack, GooSourceScanner.firstSource(player, ROCK)),
                () -> assertEquals(60, drawn));
    }

    @Test
    void gooStacksDrainBeforeAVatInAnEarlierSlot() {
        ItemStack vat = carrierStack(VatBlockItem.class);
        ItemStack goo = carrierStack(GooItem.class);
        Player player = playerHolding(vat, goo);
        GooCarrierItem vatItem = (GooCarrierItem) vat.getItem();

        assertSame(goo, GooSourceScanner.firstSource(player, ROCK));
        assertEquals(50, GooSourceScanner.deplete(player, ROCK, 50));
        verify(vatItem, never()).drawGoo(any(), any(), anyInt());
    }

    private static ItemStack carrierStack(Class<? extends Item> type) {
        Item item = mock(type, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        GooCarrierItem carrier = (GooCarrierItem) item;
        ItemStack stack = mock(ItemStack.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(item);
        doReturn(Map.of(ROCK, HELD)).when(carrier).gooContents(stack);
        doReturn(HELD).when(carrier).gooVolume(stack, ROCK);
        doAnswer(call -> Math.min(HELD, (int) call.getArgument(2))).when(carrier).drawGoo(any(), any(), anyInt());
        return stack;
    }

    private static Player playerHolding(ItemStack... stacks) {
        ItemStack empty = mock(ItemStack.class);
        when(empty.isEmpty()).thenReturn(true);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getItem(anyInt())).thenReturn(empty);
        for (int slot = 0; slot < stacks.length; slot++) {
            when(inventory.getItem(slot)).thenReturn(stacks[slot]);
        }
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(inventory);
        return player;
    }
}
