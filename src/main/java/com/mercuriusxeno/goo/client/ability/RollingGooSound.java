package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.entity.RollingGoo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * The wind a rolling goo makes as it flies: vanilla's continuous elytra
 * wind, looped and pitched low, following the ball, heard as it passes
 * near and falling silent when it ends, so frost's Orb is heard rolling
 * past (decision orb-carries-a-swirling-nova).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class RollingGooSound extends AbstractTickableSoundInstance {

    private static final float VOLUME = 1f;
    /** Low, a heavy storm of wind rather than a glide. */
    private static final float PITCH = 0.7f;

    private final RollingGoo goo;

    private RollingGooSound(RollingGoo goo) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.goo = goo;
        this.looping = true;
        this.delay = 0;
        this.volume = VOLUME;
        this.pitch = PITCH;
        follow();
    }

    /**
     * Starts the whirl as a rolling goo joins the client's level.
     *
     * @param event the entity join event
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof RollingGoo goo) {
            Minecraft.getInstance().getSoundManager().play(new RollingGooSound(goo));
        }
    }

    @Override
    public void tick() {
        if (goo.isRemoved()) {
            stop();
        } else {
            follow();
        }
    }

    private void follow() {
        this.x = goo.getX();
        this.y = goo.getY();
        this.z = goo.getZ();
    }
}
