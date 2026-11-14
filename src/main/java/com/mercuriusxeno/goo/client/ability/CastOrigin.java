package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.throwing.GloveAim;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Where a caster's cast leaves from: the palm blob lifted off the glove
 * where it stands, and the glove itself while the blob rests on the palm.
 * Every held effect and every release that sends something from the hand
 * reads this one point, so the effect comes out of the blob.
 * every-ability-casts-from-the-lifted-palm-blob
 */
public final class CastOrigin {

    private CastOrigin() {
    }

    /**
     * Where a caster's cast leaves this frame.
     *
     * @param casterId    the caster's entity id
     * @param glove       where the caster's glove stands, the origin while the blob rests on the palm
     * @param partialTick the frame's partial tick
     * @return the lifted blob's place, or the glove
     */
    public static Vec3 of(int casterId, Vec3 glove, float partialTick) {
        return LiftedPalmBlob.placeOf(casterId, partialTick).orElse(glove);
    }

    /**
     * Where the local player's cast leaves this frame: the lifted blob, or
     * the goo in the glove as the glove last drew it.
     *
     * @param mc the client
     * @return the lifted blob's place, or the glove
     */
    public static Vec3 ofLocal(Minecraft mc) {
        Vec3 glove = GloveAim.handPosition(mc.gameRenderer.getMainCamera());
        return mc.player == null ? glove
                : of(mc.player.getId(), glove, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }
}
