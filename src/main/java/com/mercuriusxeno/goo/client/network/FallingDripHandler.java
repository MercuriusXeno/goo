package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.particle.FallingDripLaunch;
import com.mercuriusxeno.goo.network.FallingDripPayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Draws a falling layer of world goo on the client: one falling drip of the
 * trail drip's family, a colour between the goo's two, launched from where
 * the layer left and living until it splats on the surface the layer lands on.
 * goo-moving-downward-falls-as-drips
 */
public final class FallingDripHandler {

    private FallingDripHandler() {
    }

    /**
     * Handles a fall's payload on the client thread.
     *
     * @param payload the fall
     * @param context the network context
     */
    public static void handle(FallingDripPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            FallingDripLaunch launch = FallingDripLaunch.of(payload);
            int rgb = ARGB.srgbLerp(mc.level.getRandom().nextFloat(), payload.dark(), payload.light());
            Particle drip = mc.particleEngine.createParticle(ColorParticleOption.create(GooParticles.TRAIL_DRIP.get(),
                    ARGB.redFloat(rgb), ARGB.greenFloat(rgb), ARGB.blueFloat(rgb)), launch.start().x,
                    launch.start().y, launch.start().z, launch.velocity().x, launch.velocity().y, launch.velocity().z);
            if (drip != null) {
                drip.setLifetime(launch.lifetime());
            }
        });
    }
}
