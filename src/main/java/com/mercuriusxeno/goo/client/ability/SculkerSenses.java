package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import com.mercuriusxeno.goo.client.network.EntitySnapshots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a Sculker senses, while its brew stands. Every sound heard sends its
 * rings out across the block faces open to air near its source, whose
 * blocks SculkerEchoRenderer draws tinted sculk teal ({@link SculkerEchoes}).
 * A mob that makes a sound shows a soft blurred sculk-teal glow around its
 * body, through walls, for its outline's time, fading; all of it draws
 * fainter the farther it stands, down to a fifth at the edge of render
 * distance. The diver also hears the sounds the server carries to it
 * from anywhere within render distance. The brew's end
 * forgets it all.
 * decision sculker-sight-persists-where-echoes-have-touched
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SculkerSenses {

    /** Sculk teal, the color every echo draws in. */
    public static final int SCULK_RGB = 0x1ED8D2;
    /** How near a sound's source an entity must stand for the sound to be its own. */
    private static final double SOUND_OWNER_REACH = 1.5;
    private static final int BLOCKS_PER_CHUNK = 16;

    private static final SculkerEchoes ECHOES = new SculkerEchoes();
    /** Each mob heard making a sound, by entity id. */
    private static final Map<Integer, Heard> HEARD = new HashMap<>();

    private SculkerSenses() {
    }

    /**
     * When a mob was last heard making a sound and how long its outline lasts.
     *
     * @param heardAt the game time its sound played
     * @param ticks   the ticks its outline lasts
     */
    private record Heard(long heardAt, int ticks) {

        long fadedAt() {
            return heardAt + ticks;
        }
    }

    /**
     * Draws the soft glow around every mob heard making a sound, while its
     * outline lasts, over the finished level.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || HEARD.isEmpty()) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<AfterimageRenderer.SoftOutline> outlines = new ArrayList<>();
        for (int id : HEARD.keySet()) {
            AfterimageRenderer.SoftOutline outline = softOutlineOf(mc, id, partialTick);
            if (outline != null) {
                outlines.add(outline);
            }
        }
        AfterimageRenderer.drawSoftOutlines(event, outlines, SCULK_RGB);
    }

    /**
     * A heard mob's soft outline this frame.
     *
     * @param mc          the client, its level loaded
     * @param id          the mob's entity id
     * @param partialTick the frame's partial tick
     * @return its outline, or null where the mob is gone or its outline has faded
     */
    private static AfterimageRenderer.@Nullable SoftOutline softOutlineOf(Minecraft mc, int id, float partialTick) {
        Entity mob = mc.level.getEntity(id);
        float strength = mob == null ? 0f : outlineStrength(mob) * SculkerVision.presence();
        EntityRenderState snapshot = strength > 0f ? EntitySnapshots.of(mc, id) : null;
        return snapshot == null ? null
                : new AfterimageRenderer.SoftOutline(snapshot, mob.getPosition(partialTick), strength);
    }

    private static float outlineStrength(Entity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        Heard heard = HEARD.get(entity.getId());
        if (player == null || heard == null) {
            return 0f;
        }
        float left = SculkerEchoes.outlineLeftAt(heard.heardAt(), heard.ticks(), entity.level().getGameTime());
        return left * SculkerEchoes.strengthAt(entity.distanceTo(player), sightReach());
    }

    /**
     * How far the local Sculker's senses reach: its render distance, in blocks.
     *
     * @return the reach
     */
    static double sightReach() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * BLOCKS_PER_CHUNK;
    }

    /**
     * Grows the echoes and forgets what has faded or left the level while
     * the local player's Sculker stands; forgets everything once it ends.
     *
     * @param event the client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !SculkerSwim.dives(player)) {
            ECHOES.clear();
            HEARD.clear();
            return;
        }
        long now = level.getGameTime();
        ECHOES.advance(now, pos -> level.getBlockState(pos).isAir());
        HEARD.entrySet().removeIf(heard -> level.getEntity(heard.getKey()) == null || now >= heard.getValue().fadedAt());
    }

    /**
     * Hears a sound the game plays at its loudness: its rings ripple out of
     * its source, and a mob it came from outlines.
     *
     * @param event the sound about to play
     */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || event.getSound() == null || !SculkerSwim.dives(player)) {
            return;
        }
        SoundInstance sound = event.getSound();
        float loudness = loudnessOf(mc, sound);
        hear(new Vec3(sound.getX(), sound.getY(), sound.getZ()), loudness, rangeOf(sound, loudness));
    }

    /**
     * How far a sound carries, as its own attenuation reaches: its
     * attenuation distance, times its loudness past ordinary.
     *
     * @param sound    the sound, resolved
     * @param loudness its loudness
     * @return its range, in blocks
     */
    private static double rangeOf(SoundInstance sound, float loudness) {
        return sound.getSound() == null ? SculkerEchoes.rangeFor(loudness)
                : sound.getSound().getAttenuationDistance() * Math.max(1f, loudness);
    }

    /**
     * A sound's loudness: its volume, 1 for an ordinary sound, read once the
     * sound has been resolved to the file it plays.
     *
     * @param mc    the client
     * @param sound the sound about to play
     * @return its loudness
     */
    private static float loudnessOf(Minecraft mc, SoundInstance sound) {
        if (sound.getSound() == null) {
            sound.resolve(mc.getSoundManager());
        }
        return sound.getSound() == null ? 1f : sound.getVolume();
    }

    /**
     * Hears a sound within render distance: its rings ripple out of its
     * source, and every mob standing at the source outlines, as a sound the
     * game plays or one the server carries from far off alike.
     *
     * @param at       where the sound played
     * @param loudness its loudness, 1 for an ordinary sound
     * @param range    how far it carries, in blocks
     */
    public static void hear(Vec3 at, float loudness, double range) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!hearsAt(mc, at)) {
            return;
        }
        long now = mc.level.getGameTime();
        ECHOES.hear(at, now, loudness, range);
        List<Entity> mobs = mc.level.getEntities(player, new AABB(at, at).inflate(SOUND_OWNER_REACH),
                LivingEntity.class::isInstance);
        int ticks = SculkerEchoes.outlineTicksFor(loudness);
        for (Entity mob : mobs) {
            HEARD.merge(mob.getId(), new Heard(now, ticks),
                    (old, fresh) -> fresh.fadedAt() >= old.fadedAt() ? fresh : old);
        }
    }

    /**
     * Whether the local Sculker hears a sound at a point: its brew stands
     * and the point lies within render distance.
     *
     * @param mc the client
     * @param at where the sound played
     * @return true where the sound is heard
     */
    private static boolean hearsAt(Minecraft mc, Vec3 at) {
        LocalPlayer player = mc.player;
        return player != null && mc.level != null && SculkerSwim.dives(player)
                && SculkerEchoes.strengthAt(at.distanceTo(player.position()), sightReach()) > 0;
    }

    /**
     * @return what the local Sculker's echoes have lit, which SculkerEchoRenderer draws
     */
    static SculkerEchoes echoes() {
        return ECHOES;
    }
}
