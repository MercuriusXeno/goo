package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.CoatSpray;
import com.mercuriusxeno.goo.network.StreamMotesPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * A stream's motes leave the caster's glove where it is drawn this frame, not
 * where the server's echo of an earlier tick put it, so a moving or turning
 * caster's spray comes out of the hand.
 * goop-and-slick-spray-throws-goo-drips-that-splat
 */
class StreamMotesTest {

    private static final int CASTER = 7;
    private static final Vec3 SENT_GLOVE = new Vec3(0.3, 1.3, 0.2);
    private static final Vec3 DRAWN_GLOVE = new Vec3(0.9, 1.1, 0.6);

    @Test
    void motesLeaveTheGloveWhereItIsDrawnThisFrame() {
        StreamMotesPayload sent = new StreamMotesPayload(CASTER, SENT_GLOVE, new Vec3(0, 0, 1),
                Identifier.fromNamespaceAndPath("goo", "goop_spray_drip"), 16, 0, new Vec3(0.2, 0, 0.1),
                Optional.of(new CoatSpray(new Vec3(0, 1.62, 0), new Vec3(0, -0.5, 1).normalize(), 8)));
        try (MockedStatic<CasterPoses> poses = mockStatic(CasterPoses.class)) {
            poses.when(() -> CasterPoses.gloveOf(eq(CASTER), any())).thenReturn(DRAWN_GLOVE);

            StreamMotesPayload launched = StreamMotes.fromTheDrawnGlove(sent);

            assertEquals(DRAWN_GLOVE, launched.apex());
            assertEquals(sent.travel(), launched.travel());
            assertEquals(sent.coat(), launched.coat());
        }
    }
}
