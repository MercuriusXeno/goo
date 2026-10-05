package com.mercuriusxeno.goo.data;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The known-items set a player carries learns an item once, answers what it
 * holds, and survives the save and the sync whole
 * (decision knowledge-capability-remembers-destroyed-items).
 */
class KnownItemsTest {

    private static final Identifier COBBLESTONE = Identifier.withDefaultNamespace("cobblestone");
    private static final Identifier DIRT = Identifier.withDefaultNamespace("dirt");

    @Nested
    class Learning {

        @Test
        void newPlayerKnowsNothing() {
            assertFalse(KnownItems.NONE.contains(COBBLESTONE));
        }

        @Test
        void learnedItemIsKnownAndOthersAreNot() {
            KnownItems known = KnownItems.NONE.with(COBBLESTONE);

            assertTrue(known.contains(COBBLESTONE));
            assertFalse(known.contains(DIRT));
        }

        @Test
        void relearningAKnownItemAnswersTheSameKnowledge() {
            KnownItems known = KnownItems.NONE.with(COBBLESTONE);

            assertSame(known, known.with(COBBLESTONE));
        }
    }

    @Nested
    class Codecs {

        private final KnownItems known = new KnownItems(Set.of(COBBLESTONE, DIRT));

        @Test
        void saveCodecRoundTripsTheSet() {
            var saved = KnownItems.CODEC.codec().encodeStart(JsonOps.INSTANCE, known).getOrThrow();

            assertEquals(known, KnownItems.CODEC.codec().parse(JsonOps.INSTANCE, saved).getOrThrow());
        }

        @Test
        void streamCodecRoundTripsTheSet() {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

            KnownItems.STREAM_CODEC.encode(buf, known);

            assertEquals(known, KnownItems.STREAM_CODEC.decode(buf));
        }
    }
}
