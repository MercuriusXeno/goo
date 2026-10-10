package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.hex.BookFusion;
import com.mercuriusxeno.goo.ability.program.FuseBooksStep;
import com.mercuriusxeno.goo.ability.program.TomeKind;
import com.mercuriusxeno.goo.ability.program.TomeStep;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.particle.HexWispParticle;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.ReagentScanner;
import com.mercuriusxeno.goo.network.TomePayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Enchant's and Fuse's floating tomes. While right click holds Enchant and
 * the player can pay for it, the enchanting table's book fades in, closed
 * and bobbing, in front of them; Fuse fades in its two books side by side,
 * once the player carries a pair to fuse. On release the server answers with
 * the choreography, which every watching client plays: Enchant's book flips
 * open, hex glyphs stream into it as its pages turn, and it snaps shut in a
 * burst of wisps with the table's chime; Fuse's two books circle each other,
 * spiral together drinking glyphs, and merge in a burst with a muffled anvil
 * ring and the chime, the one book left flipping open and shut before it
 * fades.
 * enchant-book-with-a-purple-afterimage
 * fuse-two-books-for-hex-goo
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class Tomes {

    /** Ticks Enchant's choreography lasts. */
    static final int ENCHANT_TICKS = 26;
    /** The tick Enchant's book snaps shut, its burst and chime. */
    static final int SNAP_AT = 18;
    /** Ticks Fuse's choreography lasts. */
    static final int FUSE_TICKS = 30;
    /** The tick Fuse's books merge, its burst, ring and chime. */
    static final int MERGE_AT = 20;
    /** Ticks the held tome takes to fade in. */
    private static final float HOLD_FADE_IN_TICKS = 8f;
    /** Ticks the held tome takes to fade out once let go unanswered. */
    private static final float HOLD_FADE_OUT_TICKS = 4f;
    private static final float WIDE_OPEN = 1.2f;
    private static final float AJAR = 0.35f;
    private static final int OPENING_TICKS = 4;
    private static final int SNAP_TICKS = 2;
    /** Ticks after Fuse's merge its one book starts snapping shut. */
    private static final int CLOSE_AFTER_MERGE = 8;
    /** How far in front of the eyes the tome floats, and how far below them. */
    private static final double REACH = 1.1;
    private static final double DROP = 0.3;
    /** How far apart Fuse's two books float, each from the middle. */
    private static final double PAIR_SPREAD = 0.35;
    private static final double BOB_REACH = 0.03;
    private static final float BOB_PER_TICK = 0.12f;
    private static final float BOOK_SCALE = 0.75f;
    private static final float SPINE_TILT_DEGREES = 80f;
    private static final double FUSE_TURNS = 2;
    private static final double FULL_TURN = Math.PI * 2;
    private static final float PAGE_FLIPS_PER_TICK = 0.15f;
    private static final float PAGE_FLIP_LAG = 0.5f;
    private static final float FLIP_STRETCH = 1.6f;
    private static final float FLIP_LEAD = 0.3f;
    private static final int GLYPHS_PER_TICK = 3;
    private static final double GLYPH_REACH = 1.2;
    private static final int BURST_WISPS = 14;
    private static final double BURST_SPEED = 0.18;
    private static final int BURST_SCATTER_TICKS = 4;
    private static final int BURST_FLIGHT = 12;
    private static final double CHEST = 0.7;
    private static final float CHIME_VOLUME = 1f;
    private static final float PAGE_VOLUME = 0.8f;
    private static final float RING_VOLUME = 0.35f;
    private static final float RING_PITCH = 1.6f;
    private static final int PAGE_TURN_AGAIN_AT = 9;
    private static final float WHOLE = 1f;
    private static final float HALF_SHARE = 0.5f;
    private static final float PITCH_WOBBLE = 0.25f;
    private static final float HOLD_PAGE_VOLUME = 0.4f;
    /** Where in its turn each of the two flipping pages starts, as the table's book staggers them. */
    private static final float FIRST_PAGE_PHASE = 0.25f;
    private static final float SECOND_PAGE_PHASE = 0.75f;
    /** Below this squared length a heading has no side. */
    private static final double NO_HEADING = 1e-6;
    /** The smoothstep cubic's terms: 3t^2 - 2t^3. */
    private static final float SMOOTHSTEP_SQUARE = 3f;
    private static final float SMOOTHSTEP_CUBE = 2f;
    private static final double HALF = 0.5;

    private static final Map<Integer, Show> SHOWS = new HashMap<>();
    private static float holdFade;
    private static @Nullable TomeKind heldKind;
    private static @Nullable BookModel book;

    private Tomes() {
    }

    /**
     * One choreography playing before a player.
     *
     * @param kind  Enchant's or Fuse's
     * @param start the game time it began
     */
    private record Show(TomeKind kind, long start) {
    }

    /**
     * Starts a player's tome choreography on the client thread.
     *
     * @param payload the tome payload
     * @param context the network context
     */
    public static void handle(TomePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                SHOWS.put(payload.playerId(), new Show(payload.kind(), level.getGameTime()));
            }
        });
    }

    /**
     * The tome a press holds: the selected ability's tome while right click
     * holds it and the player can pay its goo and reagents, and, for Fuse,
     * carries a pair to fuse.
     *
     * @param player the local player
     * @return the tome to fade in, or null for none
     */
    private static @Nullable TomeKind heldTome(LocalPlayer player) {
        ClientAbility ability = GloveUseTracker.showsArea() ? selectedAbility(player) : null;
        ResourceKey<GooTypeDefinition> gooType = GloveAim.selectedGooType(player);
        if (ability == null || gooType == null || !pays(player, gooType, ability)) {
            return null;
        }
        return ability.behaviors().stream().filter(TomeStep.class::isInstance)
                .map(step -> ((TomeStep) step).kind()).findFirst().orElse(null);
    }

    private static @Nullable ClientAbility selectedAbility(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        return abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
    }

    /**
     * Whether the player can pay for the ability: its goo, its reagents and,
     * for Fuse, a pair to fuse.
     *
     * @param player  the local player
     * @param gooType the ability's goo type
     * @param ability the ability
     * @return true when the press would act
     */
    private static boolean pays(LocalPlayer player, ResourceKey<GooTypeDefinition> gooType, ClientAbility ability) {
        boolean fuses = ability.behaviors().stream().anyMatch(FuseBooksStep.class::isInstance);
        return GooSourceScanner.hasEnough(player, gooType, ability.cost())
                && ReagentScanner.holdsEvery(player, ability.consumes())
                && (!fuses || BookFusion.holdsPair(player.getInventory()));
    }

    /**
     * Fades the held tome, and runs each playing choreography's sounds,
     * glyphs and bursts on their ticks, dropping each once it ends.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null || mc.isPaused()) {
            return;
        }
        fadeHeld(mc.level, player);
        runShows(mc, mc.level);
    }

    private static void fadeHeld(ClientLevel level, LocalPlayer player) {
        TomeKind held = SHOWS.containsKey(player.getId()) ? null : heldTome(player);
        if (held == null) {
            holdFade = Math.max(0f, holdFade - WHOLE / HOLD_FADE_OUT_TICKS);
            return;
        }
        if (holdFade <= 0f) {
            playAt(level, player, anchorOf(player, 1f), SoundEvents.BOOK_PAGE_TURN, HOLD_PAGE_VOLUME, WHOLE);
        }
        heldKind = held;
        holdFade = Math.min(WHOLE, holdFade + WHOLE / HOLD_FADE_IN_TICKS);
    }

    private static void runShows(Minecraft mc, ClientLevel level) {
        long now = level.getGameTime();
        Iterator<Map.Entry<Integer, Show>> shows = SHOWS.entrySet().iterator();
        while (shows.hasNext()) {
            Map.Entry<Integer, Show> show = shows.next();
            int elapsed = (int) (now - show.getValue().start());
            if (!(level.getEntity(show.getKey()) instanceof Player caster)
                    || elapsed > lengthOf(show.getValue().kind())) {
                shows.remove();
            } else {
                choreograph(mc, caster, show.getValue().kind(), elapsed);
            }
        }
    }

    private static int lengthOf(TomeKind kind) {
        return kind == TomeKind.ENCHANT ? ENCHANT_TICKS : FUSE_TICKS;
    }

    private static void choreograph(Minecraft mc, Player caster, TomeKind kind, int elapsed) {
        ClientLevel level = mc.level;
        Vec3 anchor = anchorOf(caster, 1f);
        if (turnsAPage(kind, elapsed)) {
            playAt(level, caster, anchor, SoundEvents.BOOK_PAGE_TURN, PAGE_VOLUME, WHOLE);
        }
        int burstAt = kind == TomeKind.ENCHANT ? SNAP_AT : MERGE_AT;
        if (elapsed > OPENING_TICKS && elapsed < burstAt) {
            spawnGlyphs(mc, level.getRandom(), anchor);
        }
        if (elapsed == burstAt) {
            sealWith(level, caster, anchor, kind);
            burst(mc, level.getRandom(), anchor, caster);
        }
    }

    private static boolean turnsAPage(TomeKind kind, int elapsed) {
        return elapsed == 0 || kind == TomeKind.ENCHANT && elapsed == PAGE_TURN_AGAIN_AT;
    }

    /**
     * Plays the choreography's seal: the table's chime, and for Fuse a
     * muffled anvil ring beneath it.
     *
     * @param level  the client level
     * @param caster the player the tome floats before
     * @param anchor where the tome floats
     * @param kind   Enchant's or Fuse's
     */
    private static void sealWith(ClientLevel level, Player caster, Vec3 anchor, TomeKind kind) {
        playAt(level, caster, anchor, SoundEvents.ENCHANTMENT_TABLE_USE, CHIME_VOLUME, WHOLE);
        if (kind == TomeKind.FUSE) {
            playAt(level, caster, anchor, SoundEvents.ANVIL_LAND, RING_VOLUME, RING_PITCH);
        }
    }

    private static void playAt(ClientLevel level, Entity caster, Vec3 at, SoundEvent sound, float volume,
                               float pitch) {
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume,
                pitch * (WHOLE + (caster.getRandom().nextFloat() - HALF_SHARE) * PITCH_WOBBLE), false);
    }

    private static void spawnGlyphs(Minecraft mc, RandomSource random, Vec3 into) {
        for (int i = 0; i < GLYPHS_PER_TICK; i++) {
            Vec3 from = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize()
                    .scale(GLYPH_REACH * (HALF + random.nextDouble() * HALF));
            mc.particleEngine.createParticle(GooParticles.HEX_GLYPH.get(), into.x, into.y, into.z,
                    from.x, from.y, from.z);
        }
    }

    private static void burst(Minecraft mc, RandomSource random, Vec3 from, Entity caster) {
        for (int i = 0; i < BURST_WISPS; i++) {
            Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize()
                    .scale(BURST_SPEED);
            if (mc.particleEngine.createParticle(GooParticles.HEX_WISP.get(), from.x, from.y, from.z, 0, 0, 0)
                    instanceof HexWispParticle wisp) {
                wisp.launch(0, out, BURST_SCATTER_TICKS, BURST_FLIGHT,
                        () -> caster.position().add(0, caster.getBbHeight() * CHEST, 0));
            }
        }
    }

    /**
     * Where a player's tome floats: in front of their eyes along their look,
     * a little below.
     *
     * @param player      the player
     * @param partialTick the partial tick
     * @return the tome's point
     */
    private static Vec3 anchorOf(Player player, float partialTick) {
        return player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(REACH))
                .add(0, -DROP, 0);
    }

    private static Vec3 sideOf(Player player, float partialTick) {
        Vec3 side = player.getViewVector(partialTick).cross(new Vec3(0, 1, 0));
        return side.lengthSqr() < NO_HEADING ? new Vec3(1, 0, 0) : side.normalize();
    }

    /**
     * The share of an ease from its start tick over its span: smoothstep, held at both ends.
     *
     * @param ticks the time, the partial tick among it
     * @param from  the tick the ease starts
     * @param span  the ticks it takes
     * @return 0 to 1
     */
    static float eased(float ticks, float from, float span) {
        float share = Mth.clamp((ticks - from) / span, 0f, WHOLE);
        return share * share * (SMOOTHSTEP_SQUARE - SMOOTHSTEP_CUBE * share);
    }

    /**
     * How open Enchant's book stands some ticks into its choreography:
     * flipping open, open while it drinks in glyphs, snapping shut.
     *
     * @param ticks the time, the partial tick among it
     * @return the openness, 0 shut
     */
    static float enchantOpenness(float ticks) {
        return WIDE_OPEN * (eased(ticks, 0f, OPENING_TICKS) - eased(ticks, SNAP_AT - SNAP_TICKS, SNAP_TICKS));
    }

    /**
     * How faded in a choreography's book stands: whole until its last
     * stretch, then fading out to nothing at its end.
     *
     * @param ticks  the time, the partial tick among it
     * @param fadeAt the tick the fade begins
     * @param end    the tick it ends
     * @return 0 to 1
     */
    static float showAlpha(float ticks, float fadeAt, float end) {
        return WHOLE - eased(ticks, fadeAt, end - fadeAt);
    }

    /**
     * Draws the held tome and every playing choreography after the translucent world.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || nothingToDraw()) {
            return;
        }
        bakeBookOnce(mc);
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float time = level.getGameTime() + partialTick;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Pen pen = new Pen(event.getPoseStack(), buffers, mc, mc.gameRenderer.getMainCamera().position());
        if (showsHeld(player)) {
            drawHeld(pen, player, partialTick, time);
        }
        drawShows(pen, level, time, partialTick);
        buffers.endBatch(EnchantTableRenderer.BOOK_TEXTURE.renderType(RenderTypes::entityTranslucent));
    }

    private static boolean nothingToDraw() {
        return holdFade <= 0f && SHOWS.isEmpty();
    }

    private static void bakeBookOnce(Minecraft mc) {
        if (book == null) {
            book = new BookModel(mc.getEntityModels().bakeLayer(ModelLayers.BOOK));
        }
    }

    private static boolean showsHeld(LocalPlayer player) {
        return holdFade > 0f && heldKind != null && !SHOWS.containsKey(player.getId());
    }

    private static void drawShows(Pen pen, ClientLevel level, float time, float partialTick) {
        for (Map.Entry<Integer, Show> show : SHOWS.entrySet()) {
            if (level.getEntity(show.getKey()) instanceof Player caster) {
                drawShow(pen, caster, show.getValue(), time - show.getValue().start(), partialTick);
            }
        }
    }

    private static void drawHeld(Pen pen, Player player, float partialTick, float time) {
        Vec3 anchor = anchorOf(player, partialTick).add(0, Math.sin(time * BOB_PER_TICK) * BOB_REACH, 0);
        Vec3 eye = player.getEyePosition(partialTick);
        if (heldKind == TomeKind.FUSE) {
            Vec3 side = sideOf(player, partialTick).scale(PAIR_SPREAD);
            pen.draw(anchor.add(side), eye, 0f, 0f, holdFade);
            pen.draw(anchor.subtract(side), eye, 0f, 0f, holdFade);
        } else {
            pen.draw(anchor, eye, 0f, 0f, holdFade);
        }
    }

    private static void drawShow(Pen pen, Player caster, Show show, float ticks, float partialTick) {
        Vec3 anchor = anchorOf(caster, partialTick);
        Vec3 eye = caster.getEyePosition(partialTick);
        float flip = ticks * PAGE_FLIPS_PER_TICK;
        if (show.kind() == TomeKind.ENCHANT) {
            float alpha = showAlpha(ticks, SNAP_AT + 1, ENCHANT_TICKS);
            pen.draw(anchor.add(0, eased(ticks, SNAP_AT, ENCHANT_TICKS - SNAP_AT) * DROP, 0), eye,
                    enchantOpenness(ticks), flip, alpha);
        } else {
            drawFuse(pen, caster, anchor, eye, ticks, partialTick);
        }
    }

    private static void drawFuse(Pen pen, Player caster, Vec3 anchor, Vec3 eye, float ticks, float partialTick) {
        float flip = ticks * PAGE_FLIPS_PER_TICK;
        if (ticks < MERGE_AT) {
            float spiral = eased(ticks, 0f, MERGE_AT);
            double radius = PAIR_SPREAD * (WHOLE - spiral);
            double angle = spiral * FUSE_TURNS * FULL_TURN;
            Vec3 side = sideOf(caster, partialTick);
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 offset = side.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius * HALF));
            float ajar = AJAR * eased(ticks, 0f, OPENING_TICKS);
            pen.draw(anchor.add(offset), eye, ajar, flip, WHOLE);
            pen.draw(anchor.subtract(offset), eye, ajar, flip + PAGE_FLIP_LAG, WHOLE);
            return;
        }
        float after = ticks - MERGE_AT;
        float openness = WIDE_OPEN * (eased(after, 0f, OPENING_TICKS) - eased(after, CLOSE_AFTER_MERGE, SNAP_TICKS));
        pen.draw(anchor, eye, openness, flip, showAlpha(ticks, FUSE_TICKS - OPENING_TICKS, FUSE_TICKS));
    }

    /**
     * What one frame's books draw with.
     *
     * @param pose    the level's pose stack, at the camera
     * @param buffers the buffer source
     * @param mc      the client
     * @param camera  the camera's position
     */
    private record Pen(PoseStack pose, MultiBufferSource buffers, Minecraft mc, Vec3 camera) {

        /**
         * Draws one book at a point, its spine turned toward an eye.
         *
         * @param at       where the book floats
         * @param eye      the eye it faces
         * @param openness how open it stands, 0 shut
         * @param flip     how far its pages have turned, in whole flips
         * @param alpha    how faded in it is, 0 to 1
         */
        void draw(Vec3 at, Vec3 eye, float openness, float flip, float alpha) {
            if (alpha <= 0f || book == null) {
                return;
            }
            pose.pushPose();
            pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
            pose.mulPose(Axis.YP.rotation((float) -Math.atan2(eye.z - at.z, eye.x - at.x)));
            pose.mulPose(Axis.ZP.rotationDegrees(SPINE_TILT_DEGREES));
            pose.scale(BOOK_SCALE, BOOK_SCALE, BOOK_SCALE);
            float flip1 = Mth.clamp(Mth.frac(flip + FIRST_PAGE_PHASE) * FLIP_STRETCH - FLIP_LEAD, 0f, WHOLE);
            float flip2 = Mth.clamp(Mth.frac(flip + SECOND_PAGE_PHASE) * FLIP_STRETCH - FLIP_LEAD, 0f, WHOLE);
            book.setupAnim(new BookModel.State(openness, flip1, flip2));
            book.renderToBuffer(pose, EnchantTableRenderer.BOOK_TEXTURE.buffer(mc.getAtlasManager(), buffers,
                    RenderTypes::entityTranslucent), GooSubmitter.fullbrightLight(), OverlayTexture.NO_OVERLAY,
                    ARGB.white(alpha));
            pose.popPose();
        }
    }
}
