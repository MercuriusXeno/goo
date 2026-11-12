package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.sound.FadingLoops;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.List;

/**
 * Bore's louder rumble: while the local player's bore is making progress
 * destroying blocks, Spire's earth rumble loops at their ears louder than
 * the ambient whir the bore's stream loops the whole time it is held, and
 * fades out once no block is breaking.
 * decision bore-loops-spires-earth-rumble
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BoreRumble {

    /** The progress rumble's volume, louder than the ambient whir rock_bore.json names. */
    static final float PROGRESS_VOLUME = 1.0f;
    private static final float PITCH = 1.0f;
    /** The key the progress rumble's loop lives under, apart from the stream's own whir. */
    private static final String LOOP_KEY = "goo:bore_progress_rumble";

    private BoreRumble() {
    }

    /**
     * Whether the progress rumble sounds: while the bore's pick holds a block
     * it is breaking.
     *
     * @param progressing the blocks the bore is progressing toward broken
     * @return true while any block is breaking
     */
    static boolean rumblesFor(List<BlockPos> progressing) {
        return !progressing.isEmpty();
    }

    /**
     * Keeps the progress rumble sounding one more tick while the local
     * player's bore is breaking blocks.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            return;
        }
        ClientAbility bore = BoreDrill.runningBore(player);
        BoreStep step = bore == null ? null : BoreDrill.stepOf(bore).orElse(null);
        if (step == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        ChannelAim aim = new ChannelAim(eye.add(player.getViewVector(1f).scale(bore.delivery().range())), null,
                bore.delivery().coneDegrees());
        List<BlockPos> progressing = BoreStep.progressing(eye, step.vortexNearRadius(), step.vortexFarRadius(), aim,
                cell -> level.getBlockState(cell).isAir(), cell -> level.getBlockState(cell).is(step.breaks()));
        if (rumblesFor(progressing)) {
            FadingLoops.keepAliveAtListener(LOOP_KEY, GooSoundIds.EARTH_RUMBLE, SoundSource.PLAYERS, PROGRESS_VOLUME,
                    PITCH);
        }
    }
}
