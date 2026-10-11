package com.mercuriusxeno.goo.ability.xeno;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that a mob's mutations survive its save as NBT and its sync on the
 * wire, and sum by kind (decision xeno-blob-mutates-the-struck).
 */
class MutationsTest {

    private static final Mutations WORN = Mutations.NONE
            .with(new Mutation(Mutation.Kind.SIZE, 0.5, Optional.empty()))
            .with(new Mutation(Mutation.Kind.SIZE, -0.2, Optional.empty()))
            .with(new Mutation(Mutation.Kind.HEALTH, 10, Optional.empty()))
            .with(new Mutation(Mutation.Kind.DROP, 0, Optional.of(Identifier.withDefaultNamespace("spider_eye"))));

    /**
     * The mutations a mob carries encode to NBT, the form its save writes,
     * and read back whole and in order.
     */
    @Test
    void mutationsRoundTripThroughNbt() {
        Tag saved = Mutations.CODEC.codec().encodeStart(NbtOps.INSTANCE, WORN).getOrThrow();

        assertEquals(WORN, Mutations.CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow());
    }

    /**
     * The mutations sync to watching clients whole and in order.
     */
    @Test
    void mutationsRoundTripOnTheWire() {
        ByteBuf buf = Unpooled.buffer();
        Mutations.STREAM_CODEC.encode(buf, WORN);

        assertEquals(WORN, Mutations.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    /**
     * A kind's total sums every mutation of that kind and no other.
     */
    @Test
    void totalSumsOneKind() {
        assertEquals(0.3, WORN.total(Mutation.Kind.SIZE), 1e-9);
        assertEquals(10, WORN.total(Mutation.Kind.HEALTH), 1e-9);
        assertEquals(0, WORN.total(Mutation.Kind.SPEED), 1e-9);
        assertTrue(Mutations.NONE.isEmpty());
        assertEquals(List.of(), Mutations.NONE.worn());
    }
}
