package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import com.mercuriusxeno.goo.client.network.EntitySnapshots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a Sculker senses, while its brew stands. Every sound heard sends its
 * rings out across the block faces open to air near its source, whose
 * blocks SculkerEchoRenderer draws tinted sculk teal ({@link SculkerEchoes}).
 * A mob that makes a sound shows the sculk outline for about a second,
 * fading, and leaves the echoing silhouette of the shared afterimage; all of
 * it draws fainter the farther it stands, down to a fifth at the edge of
 * render distance. The diver also hears the sounds the server carries to it
 * from anywhere within render distance. The brew's end
 * forgets it all.
 * decision sculker-sight-persists-where-echoes-have-touched
 * decision afterimage-is-one-shared-effect
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SculkerSenses {

    /** Sculk teal, the color every echo draws in. */
    public static final int SCULK_RGB = 0x1ED8D2;
    /** How near a sound's source an entity must stand for the sound to be its own. */
    private static final double SOUND_OWNER_REACH = 1.5;
    /** Ticks between one silhouette an entity leaves and the next. */
    private static final int SILHOUETTE_GAP_TICKS = 10;
    /** Ticks each silhouette takes to grow and fade. */
    private static final int SILHOUETTE_LIFE_TICKS = 16;
    private static final int BLOCKS_PER_CHUNK = 16;

    private static final SculkerEchoes ECHOES = new SculkerEchoes();
    /** Each mob heard making a sound, by entity id. */
    private static final Map<Integer, Heard> HEARD = new HashMap<>();
    /** Each heard entity's id and the game time it last left a silhouette. */
    private static final Map<Integer, Long> SILHOUETTE_AT = new HashMap<>();

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
     * Whether an entity draws its outline for the local Sculker now.
     *
     * @param entity the entity drawn
     * @return true for a mob heard making a sound whose outline has not faded, within render distance
     */
    public static boolean outlines(Entity entity) {
        return outlineStrength(entity) > 0;
    }

    /**
     * The color an entity's outline draws in: sculk teal, as strong as what
     * is left of its outline times how near it stands.
     *
     * @param entity the entity drawn
     * @return the outline's color
     */
    public static int outlineColor(Entity entity) {
        return ARGB.scaleRGB(SCULK_RGB, outlineStrength(entity));
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
            SILHOUETTE_AT.clear();
            return;
        }
        long now = level.getGameTime();
        ECHOES.advance(now, pos -> level.getBlockState(pos).isAir());
        HEARD.entrySet().removeIf(heard -> level.getEntity(heard.getKey()) == null || now >= heard.getValue().fadedAt());
        SILHOUETTE_AT.keySet().retainAll(HEARD.keySet());
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
        hear(new Vec3(sound.getX(), sound.getY(), sound.getZ()), loudnessOf(mc, sound));
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
     */
    public static void hear(Vec3 at, float loudness) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !SculkerSwim.dives(player)
                || SculkerEchoes.strengthAt(at.distanceTo(player.position()), sightReach()) <= 0) {
            return;
        }
        long now = mc.level.getGameTime();
        ECHOES.hear(at, now, loudness);
        List<Entity> mobs = mc.level.getEntities(player, new AABB(at, at).inflate(SOUND_OWNER_REACH),
                LivingEntity.class::isInstance);
        int ticks = SculkerEchoes.outlineTicksFor(loudness);
        for (Entity mob : mobs) {
            hearMob(mc, player, mob, now, ticks);
        }
    }

    /**
     * Outlines a mob heard making a sound and leaves its echoing silhouette,
     * no oftener than the silhouette gap, dimmer the farther it stands.
     *
     * @param mc     the client
     * @param player the local player
     * @param mob    the mob heard
     * @param now    the game time
     * @param ticks  how long its outline lasts
     */
    private static void hearMob(Minecraft mc, LocalPlayer player, Entity mob, long now, int ticks) {
        HEARD.merge(mob.getId(), new Heard(now, ticks), (old, fresh) -> fresh.fadedAt() >= old.fadedAt() ? fresh : old);
        Long last = SILHOUETTE_AT.get(mob.getId());
        if (last != null && now - last < SILHOUETTE_GAP_TICKS) {
            return;
        }
        SILHOUETTE_AT.put(mob.getId(), now);
        EntityRenderState snapshot = EntitySnapshots.of(mc, mob.getId());
        if (snapshot != null) {
            float strength = SculkerEchoes.strengthAt(mob.distanceTo(player), sightReach());
            Afterimages.CLIENT.add(snapshot, mob.position(), ARGB.scaleRGB(SCULK_RGB, strength), now,
                    SILHOUETTE_LIFE_TICKS);
        }
    }

    /**
     * @return what the local Sculker's echoes have lit, which SculkerEchoRenderer draws
     */
    static SculkerEchoes echoes() {
        return ECHOES;
    }
}
