package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The sound of a prism taking form on this client: as the blob grows into
 * the crystal, amethyst crunches climb in pitch, and the tick it stands
 * whole it sets solid with a resonant ring.
 * decision prism-blob-becomes-a-milky-quartz-crystal
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PrismFormingSounds {

    /** The client's forming prisms. */
    public static final PrismFormingSounds CLIENT = new PrismFormingSounds();

    /** The shares of the forming each growing crunch sounds at, and the pitch each climbs to. */
    static final float[] GROW_AT = {0f, 1f / 3, 2f / 3};
    static final float[] GROW_PITCH = {0.8f, 1.0f, 1.25f};
    /** The ring's pitch as the prism sets solid. */
    static final float SOLID_PITCH = 1.0f;
    private static final float GROW_VOLUME = 0.7f;
    private static final float SOLID_VOLUME = 1f;
    private static final float CHIME_VOLUME = 0.6f;
    private static final float CHIME_PITCH = 1.5f;

    /**
     * One sound a forming prism makes.
     *
     * @param tick  the game time it sounds at
     * @param solid whether it is the ring of the prism setting solid
     * @param pitch its pitch
     */
    record Cue(long tick, boolean solid, float pitch) {
    }

    /**
     * One forming prism.
     *
     * @param at    the prism's center
     * @param cues  the sounds it has yet to make
     */
    private record Forming(Vec3 at, List<Cue> cues) {
    }

    private final List<Forming> forming = new ArrayList<>();

    private PrismFormingSounds() {
    }

    /**
     * The sounds a prism forming from a tick over a length makes: the
     * growing crunches spread across the forming, then the ring at its end.
     *
     * @param startTick the game time it began to form
     * @param ticks     the ticks the forming takes
     * @return its cues in order
     */
    static List<Cue> cuesOf(long startTick, int ticks) {
        List<Cue> cues = new ArrayList<>();
        for (int index = 0; index < GROW_AT.length; index++) {
            cues.add(new Cue(startTick + Math.round(GROW_AT[index] * ticks), false, GROW_PITCH[index]));
        }
        cues.add(new Cue(startTick + ticks, true, SOLID_PITCH));
        return cues;
    }

    /**
     * Starts a prism forming at a block.
     *
     * @param pos       the prism's block
     * @param startTick the game time it began to form
     * @param ticks     the ticks the forming takes
     */
    public void start(BlockPos pos, long startTick, int ticks) {
        forming.add(new Forming(Vec3.atCenterOf(pos), new ArrayList<>(cuesOf(startTick, ticks))));
    }

    /**
     * Plays every cue whose tick has come and drops the prisms that are whole.
     *
     * @param level the client level
     */
    void playDue(ClientLevel level) {
        long now = level.getGameTime();
        forming.removeIf(prism -> {
            prism.cues().removeIf(cue -> {
                if (cue.tick() > now) {
                    return false;
                }
                play(level, prism.at(), cue);
                return true;
            });
            return prism.cues().isEmpty();
        });
    }

    private static void play(ClientLevel level, Vec3 at, Cue cue) {
        if (cue.solid()) {
            sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SOLID_VOLUME, cue.pitch());
            sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, CHIME_VOLUME, CHIME_PITCH);
        } else {
            sound(level, at, SoundEvents.AMETHYST_CLUSTER_PLACE, GROW_VOLUME, cue.pitch());
        }
    }

    private static void sound(ClientLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch, false);
    }

    /** Drops every forming prism, as a disconnect does. */
    public void clear() {
        forming.clear();
    }

    /**
     * Client tick: plays the forming prisms' sounds as they come due.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            CLIENT.clear();
        } else {
            CLIENT.playDue(level);
        }
    }
}
