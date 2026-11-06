package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.sound.FadingLoops;
import com.mercuriusxeno.goo.network.HeaterFlamePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The heater prisms flaming near this client: while its server's ticks keep
 * arriving, each one pours Flames' own particle fire from the prism's tip at
 * its target through {@link FireSpray}, and its fire roar loops at the prism
 * (idea heater-prism-rulings: Flames' fire roar).
 * heater-prism-flames-nearby-creatures
 * flames-draws-as-particle-style-fire
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HeaterFlames {

    /** Ticks past the last report a heater still counts as flaming, so a late tick does not break its fire. */
    static final int HEARD_GRACE_TICKS = 3;
    private static final Identifier ROAR = Identifier.withDefaultNamespace("block.fire.ambient");
    private static final float ROAR_VOLUME = 1.0f;
    private static final float ROAR_PITCH = 1.0f;

    private static final Map<BlockPos, Heater> HEATERS = new HashMap<>();

    private HeaterFlames() {
    }

    /**
     * One heater's fire on this client.
     *
     * @param tip   where its fire pours from
     * @param axis  the unit vector it points along, at its target
     * @param heard the game time its last tick arrived
     */
    record Heater(Vec3 tip, Vec3 axis, long heard) {
    }

    /**
     * Takes a heater's tick on the client thread.
     *
     * @param payload the heater's tick
     * @param context the network context
     */
    public static void onPayload(HeaterFlamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                HEATERS.put(payload.prism(), new Heater(payload.tip(), payload.axis(), level.getGameTime()));
            }
        });
    }

    /**
     * Each client tick: a heater whose ticks stopped goes out, and a flaming
     * one pours its fire and keeps its roar going.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            HEATERS.clear();
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<BlockPos, Heater>> heaters = HEATERS.entrySet().iterator();
        while (heaters.hasNext()) {
            Map.Entry<BlockPos, Heater> entry = heaters.next();
            Heater heater = entry.getValue();
            if (now - heater.heard() > HEARD_GRACE_TICKS) {
                heaters.remove();
                continue;
            }
            FireSpray.pour(level, spoutOf(heater));
            FadingLoops.keepAlive(entry.getKey(), ROAR, SoundSource.BLOCKS, ROAR_VOLUME, ROAR_PITCH, heater.tip());
        }
    }

    /**
     * The spout a heater pours from: its tip, at its target, at Flames' own reach and cone.
     *
     * @param heater the heater
     * @return the spout
     */
    static FireSpray.Spout spoutOf(Heater heater) {
        return new FireSpray.Spout(heater.tip(), heater.axis(), FireSpray.FLAMES_RANGE, FireSpray.FLAMES_CONE_DEGREES);
    }
}
