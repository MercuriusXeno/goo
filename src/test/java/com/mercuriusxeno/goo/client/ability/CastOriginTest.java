package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A cast leaves from the lifted palm blob where it stands, for the local
 * player and another caster alike, and from the glove while the blob rests
 * on the palm.
 * every-ability-casts-from-the-lifted-palm-blob
 */
class CastOriginTest {

    private static final long NOW = 400L;
    /** Part way through the lift, the blob between the palm and the steady point. */
    private static final long PART_WAY = NOW + PalmBlobHop.HOP_TICKS + PalmBlobHop.EASE_TICKS / 2;
    private static final Vec3 GLOVE = new Vec3(3.4, 64.9, -1.7);

    private static void heldThrough(long last) {
        for (long tick = NOW; tick <= last; tick++) {
            LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, tick);
        }
    }

    @Test
    void anotherCastersCastLeavesTheLiftedBlobWhereItStands() {
        try (SprayingClient client = SprayingClient.another(GooTypes.ZOO)) {
            client.at(PART_WAY);
            heldThrough(PART_WAY);

            assertEquals(LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).orElseThrow().place(),
                    CastOrigin.of(SprayingClient.CASTER, GLOVE, 0f));
        }
    }

    @Test
    void theLocalPlayersCastLeavesTheLiftedBlobWhereItStands() {
        try (SprayingClient client = SprayingClient.local(GooTypes.ZOO)) {
            client.at(PART_WAY);
            heldThrough(PART_WAY);

            assertEquals(LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).orElseThrow().place(),
                    CastOrigin.ofLocal(Minecraft.getInstance()));
        }
    }

    @Test
    void aCastWithTheBlobOnThePalmLeavesTheGlove() {
        try (SprayingClient client = SprayingClient.another(GooTypes.ZOO)) {
            client.at(NOW);

            assertEquals(GLOVE, CastOrigin.of(SprayingClient.CASTER, GLOVE, 0f));
        }
    }
}
