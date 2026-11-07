package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.ClientGooValues;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The wheel and the crosshair panel price an item-priced cost from the client's synced goo values, the cost
 * expression reaching the client through the sync payload as its text
 * (decision ability-cost-builds-as-a-one-type-by-type-value). Swaps the client value source and restores it.
 */
class ClientAbilityPriceTest {

    private static final Identifier COBBLESTONE = Identifier.withDefaultNamespace("cobblestone");
    private static final int COBBLESTONE_MB = 250;
    private static final int NO_REAGENTS = 0;

    private Supplier<IGooValueLookup> standing;

    @AfterEach
    void restoreTheClientValueSource() {
        if (standing != null) {
            ClientGooValues.readFrom(standing);
        }
    }

    private static ClientAbility synced(AbilityDefinition definition) {
        AbilitySyncPayload sent = new AbilitySyncPayload(
                AbilitySyncPayload.gloveEntries(definition.gooType(), List.of(definition)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        AbilitySyncPayload.STREAM_CODEC.encode(buf, sent);
        return ClientAbility.fromEntry(AbilitySyncPayload.STREAM_CODEC.decode(buf).entries().getFirst());
    }

    @Test
    void anItemPricedCostReadsThePricedMbOnTheWheel() {
        String json = AbilityJson.read("unstable_lurker")
                .replaceFirst("\"cost\"\\s*:\\s*\\d+", "\"cost\": \"3 * minecraft:cobblestone\"");
        AbilityDefinition lurker = AbilityJson.decodeText("unstable_lurker", json);
        IGooValueLookup values = mock(IGooValueLookup.class);
        when(values.lookup(COBBLESTONE)).thenReturn(new GooValue(Map.of(lurker.gooType(), COBBLESTONE_MB)));
        standing = ClientGooValues.readFrom(() -> values);

        ClientAbility ability = synced(lurker);

        assertEquals(750, ability.price());
        assertEquals(3, AffordedCount.reckon(ability, 2250, NO_REAGENTS).orElseThrow().count());
    }
}
