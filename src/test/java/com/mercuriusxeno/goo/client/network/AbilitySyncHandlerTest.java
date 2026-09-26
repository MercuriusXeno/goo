package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The client prices a throw at every stack exactly as the server does, from
 * the cost formula and step program the sync carries, over every shipped
 * ability and so every cost formula (decision unaffordable-click-does-nothing).
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
    void clientCostEqualsServerCostAtEveryStack(Path file) {
        AbilityDefinition definition = AbilityJson.decode(file);
        for (AbilitySyncPayload.Entry entry : roundTrip(definition)) {
            ClientAbility client = ClientAbility.fromEntry(entry);
            IntStream.rangeClosed(0, definition.chain().maxStacks()).forEach(stack ->
                    assertEquals(definition.throwCost(stack), client.throwCost(stack),
                            definition.id() + " at stack " + stack));
        }
    }
}
