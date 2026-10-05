package com.mercuriusxeno.goo.client.network;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
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
    void bothSidesPriceAThrowAtTheJsonsCost(Path file) throws IOException {
        int jsonCost;
        try (Reader reader = Files.newBufferedReader(file)) {
            jsonCost = JsonParser.parseReader(reader).getAsJsonObject().get("cost").getAsInt();
        }
        AbilityDefinition definition = AbilityJson.decode(file);
        assertEquals(jsonCost, definition.cost(), definition.id() + " server cost");
        for (AbilitySyncPayload.Entry entry : roundTrip(definition)) {
            ClientAbility client = ClientAbility.fromEntry(entry);
            assertEquals(jsonCost, client.cost(), definition.id() + " synced cost");
        }
    }
}
