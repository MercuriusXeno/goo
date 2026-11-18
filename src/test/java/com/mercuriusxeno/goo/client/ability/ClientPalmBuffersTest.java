package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.crystal.StackHover;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The palm blob's cube follows the fill the server holds: a sync naming 40%
 * draws the cube at 0.737 of its full edge, and between syncs the drawn fill
 * grows at the synced speed up to the synced cap.
 * decision palm-blob-and-cutaway-cuboid-size-by-the-buffers-volume
 */
class ClientPalmBuffersTest {

    private static final int CASTER = 5;
    private static final long SYNCED_AT = 100;
    private static final int FORTY = 80;
    private static final int A_PERCENT = 2;
    private static final int SEVENTY = 140;
    private static final double WITHIN = 1e-6;

    @AfterEach
    void clearTheBuffers() {
        ClientPalmBuffers.clear();
    }

    @Test
    void aSyncNamingFortyPercentDrawsTheCubeAtSevenThreeSevenOfItsEdge() {
        ClientPalmBuffers.synced(CASTER, FORTY, A_PERCENT, SEVENTY, SYNCED_AT);
        double edge = StackHover.edgeAtFill(ClientPalmBuffers.fillAt(CASTER, SYNCED_AT)) / StackHover.edgeAtFill(1);
        assertEquals(0.737, edge, 0.001);
    }

    @Test
    void betweenSyncsTheFillGrowsAtTheSyncedSpeedUpToTheCap() {
        ClientPalmBuffers.synced(CASTER, FORTY, A_PERCENT, SEVENTY, SYNCED_AT);
        assertEquals(0.5, ClientPalmBuffers.fillAt(CASTER, SYNCED_AT + 10), WITHIN, "10 ticks at 1% a tick");
        assertEquals(0.7, ClientPalmBuffers.fillAt(CASTER, SYNCED_AT + 40), WITHIN, "40 ticks, stopped at the cap");
    }
}