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
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The heater prisms flaming near this client: each one's fire, as its
 * server's ticks report it, drawn by {@link FireStream} as Flames' own fire
 * from the prism's tip, growing out over Flames' quarter second when it
 * starts and burning away once the ticks stop, and its fire roar looping at
 * the prism while it flames (idea heater-prism-rulings: Flames' fire roar).
 * heater-prism-flames-nearby-creatures
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
     * @param tip        where its fire pours from
     * @param axis       the way it points
     * @param since      the game time it began flaming
     * @param heard      the game time its last tick arrived
     * @param releasedAt the game time it stopped, or NaN while it flames
     */
    record Heater(Vec3 tip, Vec3 axis, double since, double heard, double releasedAt) {
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
                heard(payload, level.getGameTime());
            }
        });
    }

    static void heard(HeaterFlamePayload payload, double now) {
        Heater was = HEATERS.get(payload.prism());
        boolean starts = was == null || !Double.isNaN(was.releasedAt());
        double since = starts ? now : was.since();
        HEATERS.put(payload.prism(), new Heater(payload.tip(), payload.axis(), since, now, Double.NaN));
    }

    /**
     * Each client tick: a heater whose ticks stopped burns away, and a
     * flaming one keeps its roar going.
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
        for (Map.Entry<BlockPos, Heater> entry : new ArrayList<>(HEATERS.entrySet())) {
            Heater heater = entry.getValue();
            if (Double.isNaN(heater.releasedAt())) {
                if (now - heater.heard() > HEARD_GRACE_TICKS) {
                    entry.setValue(new Heater(heater.tip(), heater.axis(), heater.since(), heater.heard(), now));
                } else {
                    FadingLoops.keepAlive(entry.getKey(), ROAR, SoundSource.BLOCKS, ROAR_VOLUME, ROAR_PITCH,
                            heater.tip());
                }
            } else if (now - heater.releasedAt() > FireStream.TRAVEL_TICKS) {
                HEATERS.remove(entry.getKey());
            }
        }
    }

    /**
     * The heaters to draw this frame.
     *
     * @return each heater's fire, flaming or burning away
     */
    static List<Heater> burning() {
        return List.copyOf(HEATERS.values());
    }

    /**
     * The fire's stretch a heater burns now, as Flames' own grows and burns away.
     *
     * @param heater the heater
     * @param now    the game time with the partial tick
     * @param range  its reach
     * @return the stretch, or null once it has burned away
     */
    static FireStream.@Nullable JetSpan spanOf(Heater heater, double now, double range) {
        double released = Double.isNaN(heater.releasedAt()) ? Double.NaN : now - heater.releasedAt();
        FireStream.JetSpan span = FireStream.jetSpan(now - heater.since(), released, range);
        return span.tail() >= span.front() ? null : span;
    }
}
