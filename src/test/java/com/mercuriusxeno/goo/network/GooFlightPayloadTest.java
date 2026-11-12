package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The flight broadcast carries the thrown ability's delivery whole, so the
 * client renders the flight without a registry (decision delivery-block-in-ability-json),
 * and the exact point the flight lands at (decision aim-point-follows-the-cursor).
 */
class GooFlightPayloadTest {

    @Test
    void deliveryRoundTripsThroughTheStreamCodec() {
        Delivery delivery = new Delivery(DeliveryKind.STREAM, 3.5, 6, 30, 10, false, java.util.Optional.empty(), 0.15);
        GooFlightPayload sent = new GooFlightPayload(1, 2, 3, "goo:blaze", -1, new BlockPos(4, 5, 6), 1,
                7, false, "goo:blaze_fixture", delivery, new Vec3(4.25, 6.0, 6.5));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        GooFlightPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(sent, GooFlightPayload.STREAM_CODEC.decode(buf));
    }

    /** A flight through a linked gate carries the gate and the point on its face (decision aim-and-shots-carry-through-linked-gates). */
    @Test
    void theGateAFlightPassesRoundTripsThroughTheStreamCodec() {
        Gate gate = new Gate(ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld")),
                new AABB(new BlockPos(0, 0, 0)).minmax(new AABB(new BlockPos(0, 1, 0))), Direction.NORTH,
                Direction.UP.getUnitVec3());
        GooFlightPayload sent = new GooFlightPayload(1, 2, 3, "goo:crystal", -1, new BlockPos(4, 5, 6), 1,
                7, false, "goo:crystal_prism", Delivery.ARC, new Vec3(4.5, 6.0, 6.5), Optional.of(gate),
                new Vec3(0.5, 1.0, 0.0), Vec3.ZERO);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        GooFlightPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(sent, GooFlightPayload.STREAM_CODEC.decode(buf), "A flight's gate and face point should "
                + "reach the client whole");
    }

    /** A flight carries its thrower's travel velocity (decision thrown-blob-leaves-the-hand-with-a-fading-drift). */
    @Test
    void theThrowersTravelVelocityRoundTripsThroughTheStreamCodec() {
        Vec3 travel = new Vec3(0.28, -0.08, 0.1);
        GooFlightPayload sent = new GooFlightPayload(1, 2, 3, "goo:crystal", -1, new BlockPos(4, 5, 6), 1,
                7, false, "goo:crystal_prism", Delivery.ARC, new Vec3(4.5, 6.0, 6.5), Optional.empty(), Vec3.ZERO,
                travel);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        GooFlightPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(travel, GooFlightPayload.STREAM_CODEC.decode(buf).travel());
    }
}
