package com.mercuriusxeno.goo.client.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The client prices a throw at every stack exactly as the server does, from
 * the flat cost the sync carries, over every shipped ability
 * (decisions unaffordable-click-does-nothing, flat-cost-per-throw).
 */
class AbilitySyncHandlerTest {

    static List<Path> shippedAbilities() {
        return AbilityJson.files();
    }

    private static List<AbilitySyncPayload.Entry> roundTrip(AbilityDefinition definition) {
        AbilitySyncPayload sent = new AbilitySyncPayload(
                AbilitySyncPayload.gloveEntries(definition.gooType(), List.of(definition)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        AbilitySyncPayload.STREAM_CODEC.encode(buf, sent);
        return AbilitySyncPayload.STREAM_CODEC.decode(buf).entries();
    }

    @ParameterizedTest
    @MethodSource("shippedAbilities")
    void bothSidesPriceAThrowAtTheJsonsCostAndUpkeep(Path file) throws IOException {
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }
        // self-effects-trickle-until-ended: a held effect names an upkeep and no cost
        int jsonUpkeep = json.has("upkeep") ? json.get("upkeep").getAsInt() : 0;
        AbilityDefinition definition = AbilityJson.decode(file);
        // ability-cost-builds-as-a-one-type-by-type-value: an expression cost reaches the client as written
        if (json.has("cost") && json.getAsJsonPrimitive("cost").isString()) {
            String written = json.get("cost").getAsString();
            assertEquals(written, definition.cost().source(), definition.id() + " server cost");
            roundTrip(definition).forEach(entry -> assertEquals(written,
                    ClientAbility.fromEntry(entry).costPrice().source(), definition.id() + " synced cost"));
            return;
        }
        int jsonCost = json.has("cost") ? json.get("cost").getAsInt() : 0;
        assertEquals(jsonCost, definition.cost().in(GooValueTable.EMPTY), definition.id() + " server cost");
        for (AbilitySyncPayload.Entry entry : roundTrip(definition)) {
            ClientAbility client = ClientAbility.fromEntry(entry);
            assertEquals(jsonCost, client.cost(), definition.id() + " synced cost");
            assertEquals(jsonUpkeep, client.upkeep(), definition.id() + " synced upkeep");
        }
    }

    /**
     * A stream draining by what it lays reads its price per placement on the
     * petal, never a rate a second or the seconds the holdings afford.
     * goop-drains-per-cubic-meter-of-slime-placed
     * dynamic-costs-keep-the-mb-quantity
     */
    @Nested
    class PlacementPricedPetals {

        private static final int PLENTY = 1000;

        @Test
        void goopsPetalReadsItsCostPerCubicMeter() {
            AbilityDefinition goop = AbilityJson.decode("weird_goop");
            String perCubicMeter = GooFormat.formatMixed(goop.cost().exactIn(GooValueTable.EMPTY)) + "/m³";

            for (AbilitySyncPayload.Entry entry : roundTrip(goop)) {
                assertEquals(perCubicMeter, ClientAbility.fromEntry(entry).petalLabel(PLENTY, 0));
            }
        }
    }
}
