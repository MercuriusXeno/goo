package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.PortalColor;
import com.mercuriusxeno.goo.network.ScanWavePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The client renderers a scan's wave draws with, each registered under the
 * visual name an ability's scan_visual step names, so the JSON's
 * choreography picks the drawing (decisions ability-json-names-its-choreography,
 * click-is-a-hit-scan-drawn-as-a-delayed-wave).
 */
public final class ScanVisuals {

    /** The visual Pulse's Click names: pulse red rings travelling to the landing. */
    public static final String SOUND_WAVE = "sound_wave";

    /**
     * A renderer that starts drawing one wave.
     */
    @FunctionalInterface
    public interface ScanVisual {

        /**
         * Starts drawing a wave.
         *
         * @param wave     the wave the server sent
         * @param gameTime the client game tick it arrived on
         */
        void start(ScanWavePayload wave, long gameTime);
    }

    /**
     * The visual Glow's Bulb names: a dart of light from the palm blob that
     * puffs its motes out where the crystal forms.
     * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
     */
    public static final String LIGHT_DART = "light_dart";


    private static final Map<String, ScanVisual> RENDERERS = new HashMap<>();

    static {
        RENDERERS.put(SOUND_WAVE, SoundWaves::start);
        RENDERERS.put(LIGHT_DART, LightDarts::start);
        // operator UAT 2026-11-18: each Portal shot darts a small fast Quantum blob in its portal's color
        for (PortalColor color : PortalColor.values()) {
            RENDERERS.put(color.shotVisual(), (wave, gameTime) -> QuantumBlobDarts.start(wave, gameTime, color));
        }
    }

    private ScanVisuals() {
    }

    /**
     * The renderer registered under a visual name.
     *
     * @param visual the visual's name
     * @return the renderer, empty for a name none registers under
     */
    public static Optional<ScanVisual> rendererFor(String visual) {
        return Optional.ofNullable(RENDERERS.get(visual));
    }

    /**
     * Starts drawing a wave the server sent with the renderer its visual names.
     *
     * @param payload the wave
     * @param context the network context
     */
    public static void onPayload(ScanWavePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                rendererFor(payload.visual()).ifPresent(renderer -> renderer.start(payload, mc.level.getGameTime()));
            }
        });
    }
}
