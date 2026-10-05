package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Draws a standing heart overlay over the vanilla health bar: while Kindle
 * stands, each real heart reads ash and each shielded one reads ember, and
 * while Barkskin stands each shielded heart reads bark (decisions
 * overlay-hearts-are-an-elemental-overshield, kindle-ember-hearts-ash-and-retaliate
 * and barkskin-bark-hearts-thorn-and-burn). The layer wraps vanilla's health
 * layer and lays its sprites on the slots vanilla drew, mirroring vanilla's
 * slot layout, low-health jiggle and regeneration bounce.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HeartOverlayHud {

    private static final String SPRITE_PREFIX = "hud/heart/";
    private static final Identifier EMBER_FULL = sprite("ember_full");
    private static final Identifier EMBER_HALF = sprite("ember_half");
    private static final Identifier ASH_FULL = sprite("ash_full");
    private static final Identifier ASH_HALF = sprite("ash_half");
    private static final Identifier BARK_FULL = sprite("bark_full");
    private static final Identifier BARK_HALF = sprite("bark_half");
    private static final int HEART_SIZE = 9;
    /** The smoldering crawl's mean opacity, the swing it pulses by and how fast. */
    private static final float SMOLDER_ALPHA = 0.6f;
    private static final float SMOLDER_PULSE = 0.25f;
    private static final float SMOLDER_PULSE_RATE = 0.7f;
    /** A slot index no slot holds, for a bar with no regeneration bounce. */
    private static final int NO_BOUNCE = -1;
    private static final int SLOT_SPACING = 8;
    private static final int HEARTS_PER_ROW = 10;
    private static final int ROW_HEIGHT = 10;
    private static final int MIN_ROW_HEIGHT = 3;
    private static final int ROWS_BEFORE_SQUEEZE = 2;
    private static final int BAR_HALF_WIDTH = 91;
    private static final int HALF = 2;
    private static final float POINTS_PER_HEART = 2.0f;
    /** The seed multiplier vanilla's health bar seeds its jiggle with. */
    private static final int JIGGLE_SEED = 312_871;
    /** Vanilla jiggles the bar at or under two hearts of health and absorption. */
    private static final int JIGGLE_HEALTH = 4;
    private static final int REGEN_BOUNCE_PAD = 5;
    private static final int REGEN_BOUNCE = 2;

    private HeartOverlayHud() {
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(Goo.MODID, SPRITE_PREFIX + name);
    }

    /**
     * Wraps the vanilla health layer so the overlay draws over its hearts.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.PLAYER_HEALTH, HeartOverlayHud::overHealth);
    }

    private static GuiLayer overHealth(GuiLayer vanilla) {
        return (graphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            int leftHeightBefore = mc.gui.leftHeight;
            vanilla.render(graphics, deltaTracker);
            LocalPlayer player = mc.player;
            if (player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer()) {
                HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
                if (overlay.stands()) {
                    paint(graphics, mc.gui, player, overlay, new BarFrame(leftHeightBefore,
                            deltaTracker.getGameTimeDeltaPartialTick(false)));
                }
            }
        };
    }

    /**
     * The height of one health row, squeezed as vanilla squeezes it once the
     * bar runs past two rows.
     *
     * @param maxHealth  the health bar's maximum
     * @param absorption the absorption points, rounded up
     * @return the row height in pixels
     */
    static int rowHeight(float maxHealth, int absorption) {
        int rows = Mth.ceil((maxHealth + absorption) / POINTS_PER_HEART / HEARTS_PER_ROW);
        return Math.max(ROW_HEIGHT - (rows - ROWS_BEFORE_SQUEEZE), MIN_ROW_HEIGHT);
    }

    /**
     * The left edge of a heart slot, ten slots to a row.
     *
     * @param slot  the heart slot, from the left
     * @param xLeft the bar's left edge
     * @return the slot's left edge
     */
    static int slotX(int slot, int xLeft) {
        return xLeft + slot % HEARTS_PER_ROW * SLOT_SPACING;
    }

    /**
     * The top edge of a heart slot, each further row stacking upward.
     *
     * @param slot      the heart slot, from the left
     * @param yBase     the bottom row's top edge
     * @param rowHeight the row height
     * @return the slot's top edge
     */
    static int slotY(int slot, int yBase, int rowHeight) {
        return yBase - slot / HEARTS_PER_ROW * rowHeight;
    }

    /**
     * The sprites an overlay heart draws, bottom first. The shield shows as
     * many halves as it holds, never more than the real heart under it:
     * Kindle lays ash over the whole real heart and ember over its shielded
     * halves, Barkskin lays bark over its shielded halves and leaves the rest
     * to vanilla's red heart.
     *
     * @param kind         the overlay's kind
     * @param shieldHalves the half hearts of shield over the slot
     * @param realHalves   the half hearts of real health in the slot, one or two
     * @return the sprites, bottom first
     */
    static List<Identifier> heartSprites(HeartKind kind, int shieldHalves, int realHalves) {
        int shown = Math.min(shieldHalves, realHalves);
        List<Identifier> sprites = new ArrayList<>();
        if (kind == HeartKind.BARKSKIN) {
            // barkskin-bark-hearts-thorn-and-burn: bark hearts wear oak bark over normal hearts
            addHalves(sprites, shown, BARK_HALF, BARK_FULL);
            return sprites;
        }
        addHalves(sprites, realHalves, ASH_HALF, ASH_FULL);
        addHalves(sprites, shown, EMBER_HALF, EMBER_FULL);
        return sprites;
    }

    private static void addHalves(List<Identifier> sprites, int halves, Identifier half, Identifier full) {
        if (halves >= HeartOverlay.FULL_SHIELD) {
            sprites.add(full);
        } else if (halves > 0) {
            sprites.add(half);
        }
    }

    /**
     * What one frame of the bar reads beyond the player: where vanilla's health
     * row began and how far into the tick the frame falls.
     *
     * @param leftHeightBefore the gui's left stack height before vanilla drew health
     * @param partialTick      the fraction of the tick elapsed
     */
    private record BarFrame(int leftHeightBefore, float partialTick) {
    }

    private static void paint(GuiGraphicsExtractor graphics, Gui gui, LocalPlayer player, HeartOverlay overlay,
                              BarFrame frame) {
        int health = Mth.ceil(player.getHealth());
        BarLayout layout = layout(graphics, gui, player, frame.leftHeightBefore());
        SlotPainter painter = new SlotPainter(graphics, overlay, gui.getGuiTicks(), frame.partialTick(),
                RegrowCrawl.crawl(overlay, player.getHealth(), player.level().getGameTime() + frame.partialTick()));
        for (int slot = 0; slot < HeartOverlay.filledSlots(health); slot++) {
            painter.paint(slot, layout.x(slot), layout.y(slot), Math.min(HeartOverlay.FULL_SHIELD, health - slot * HALF));
        }
    }

    /**
     * Where vanilla laid this frame's heart slots, mirrored.
     *
     * @param xLeft      the bar's left edge
     * @param yBase      the bottom row's top edge
     * @param rowHeight  the row height
     * @param jiggle     each slot's low-health jiggle
     * @param bounceSlot the slot regeneration bounces, or none
     */
    private record BarLayout(int xLeft, int yBase, int rowHeight, int[] jiggle, int bounceSlot) {

        int x(int slot) {
            return slotX(slot, xLeft);
        }

        int y(int slot) {
            return slotY(slot, yBase, rowHeight) + jiggle[slot] - (slot == bounceSlot ? REGEN_BOUNCE : 0);
        }
    }

    private static BarLayout layout(GuiGraphicsExtractor graphics, Gui gui, LocalPlayer player, int leftHeightBefore) {
        int health = Mth.ceil(player.getHealth());
        float maxHealth = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), health);
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int[] jiggle = jiggle(gui.getGuiTicks(), Mth.ceil(maxHealth / POINTS_PER_HEART)
                + Mth.ceil(absorption / POINTS_PER_HEART), health + absorption <= JIGGLE_HEALTH);
        int bounceSlot = player.hasEffect(MobEffects.REGENERATION)
                ? gui.getGuiTicks() % Mth.ceil(maxHealth + REGEN_BOUNCE_PAD) : NO_BOUNCE;
        return new BarLayout(graphics.guiWidth() / HALF - BAR_HALF_WIDTH, graphics.guiHeight() - leftHeightBefore,
                rowHeight(maxHealth, absorption), jiggle, bounceSlot);
    }

    /**
     * Paints one frame's overlay hearts, slot by slot.
     *
     * @param graphics    the gui graphics
     * @param overlay     the player's overlay
     * @param guiTicks    the gui tick
     * @param partialTick the fraction of the tick elapsed
     * @param crawl       the half regrowing now, if any
     */
    private record SlotPainter(GuiGraphicsExtractor graphics, HeartOverlay overlay, int guiTicks, float partialTick,
                               Optional<RegrowCrawl.Crawl> crawl) {

        void paint(int slot, int x, int y, int realHalves) {
            for (Identifier sprite : heartSprites(overlay.kind(), overlay.shieldAt(slot), realHalves)) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, HEART_SIZE, HEART_SIZE);
            }
            crawl.filter(regrowing -> overlay.kind() == HeartKind.KINDLE && regrowing.slot() == slot
                            && regrowing.fromHalf() < realHalves)
                    .ifPresent(regrowing -> paintCrawl(graphics, overlay.kind(), regrowing, guiTicks, x, y));
            if (overlay.kind() == HeartKind.KINDLE) {
                paintSparks(graphics, EmberSparks.sparks(slot, Math.min(overlay.shieldAt(slot), realHalves),
                        guiTicks, partialTick), x, y);
            }
        }
    }

    /**
     * Paints a regrowing half's crawl: the shield's sprite revealed row by row
     * up to the front, a smoldering, pulsing ember over Kindle's ash.
     *
     * @param graphics the gui graphics
     * @param kind     the overlay's kind
     * @param crawl    the half regrowing
     * @param guiTicks the gui tick
     * @param x        the slot's left edge
     * @param y        the slot's top edge
     */
    private static void paintCrawl(GuiGraphicsExtractor graphics, HeartKind kind, RegrowCrawl.Crawl crawl, int guiTicks,
                                   int x, int y) {
        boolean smolder = kind == HeartKind.KINDLE;
        Identifier sprite = smolder ? EMBER_FULL : BARK_FULL;
        float alpha = smolder ? SMOLDER_ALPHA + SMOLDER_PULSE * Mth.sin(guiTicks * SMOLDER_PULSE_RATE) : 1f;
        int left = x + crawl.fromHalf() * (HEART_SIZE - RegrowCrawl.HALF_WIDTH);
        for (int row = 0; row < HEART_SIZE; row++) {
            int reach = RegrowCrawl.rowReach(row, crawl.progress(), guiTicks, smolder);
            if (reach > 0) {
                graphics.enableScissor(left, y + row, left + reach, y + row + 1);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, HEART_SIZE, HEART_SIZE, alpha);
                graphics.disableScissor();
            }
        }
    }
    private static void paintSparks(GuiGraphicsExtractor graphics, List<EmberSparks.Spark> sparks, int x, int y) {
        for (EmberSparks.Spark spark : sparks) {
            int left = x + Math.round(spark.x());
            int top = y + Math.round(spark.y());
            graphics.fill(left, top, left + 1, top + 1, spark.argb());
        }
    }

    /**
     * Replays vanilla's jiggle draw: seeded by the gui tick, one roll per slot
     * from the last slot down, only while the bar is low.
     *
     * @param guiTicks the gui's tick count
     * @param slots    the heart slots vanilla draws, health and absorption
     * @param low      whether the bar is low enough to jiggle
     * @return each slot's downward offset
     */
    private static int[] jiggle(int guiTicks, int slots, boolean low) {
        int[] offsets = new int[slots];
        if (low) {
            // vanilla multiplies in int, overflow and all, before seeding
            int seed = guiTicks * JIGGLE_SEED;
            RandomSource random = RandomSource.create(seed);
            for (int slot = slots - 1; slot >= 0; slot--) {
                offsets[slot] = random.nextInt(HALF);
            }
        }
        return offsets;
    }
}
