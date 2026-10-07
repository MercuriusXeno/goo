package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.network.OfferedAbility;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypeNames;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Draws the glove's radial wheel from a {@link RadialWheel}: one ring of
 * petals from the hub to the rim, type petals with their icon alone and the
 * open type's ability petals with icon, name and cost, or on a locked one its
 * required items alone with the learned ones slashed, the open type's name
 * and holdings in the hub.
 * decision abilities-replace-the-hovered-type
 * decision locked-petal-lists-the-unlearned-items
 */
final class RadialWheelRenderer {

    private static final int NORMAL_ALPHA = 0xAA;
    private static final int HOVER_ALPHA = 0xDD;
    private static final int DISABLED_ALPHA = 0x55;
    private static final float DISABLED_DIM = 0.4f;
    /** Shade a resting wedge's fluid fill blits under, below the hovered wedge's full white. */
    private static final int REST_SHADE = 0xCC;
    private static final int DISABLED_SHADE = (int) (0xFF * DISABLED_DIM);
    private static final int HUB_COLOR = 0x44FFFFFF;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int HOVER_TEXT_COLOR = 0xFFFFFF00;
    /** The color a disabled petal's words draw in. */
    static final int DISABLED_TEXT_COLOR = 0xFF888888;
    /** The near-black border every word draws inside. */
    static final int OUTLINE_COLOR = 0xFF101010;
    /** How many pixels the border reaches past each side of a word. */
    private static final int OUTLINE_WIDTH = 1;
    private static final int TYPE_ICON_SIZE = 11;
    /** ability-icons-read-16x16 */
    static final int ABILITY_ICON_SIZE = 16;
    private static final int ABILITY_ICON_OFFSET = ABILITY_ICON_SIZE / 2;
    private static final int LABEL_GAP = 1;
    /** The side of the square an item icon draws in. */
    static final int ITEM_ICON_SIZE = 16;
    /** How far apart, center to center along the petal, a locked petal's item icons stand. */
    static final int ITEM_STRIDE = 18;
    /** The red a learned item's slash draws in. */
    static final int SLASH_COLOR = 0xFFE02020;
    /** How many pixels tall each column of a slash stands. */
    private static final int SLASH_THICKNESS = 2;
    private static final int HALF = 2;
    /**
     * How far past the petal's outer radius a closed petal's icon centers:
     * the reach from the icon's center to the badge's far corner, one icon
     * right and half an icon up, plus a pixel, so no pixel of either lies
     * inside the petal.
     * decision icons-slide-in-from-behind-the-tip
     */
    static final double ENTRY_REACH = Math.hypot(ABILITY_ICON_SIZE + ABILITY_ICON_OFFSET, ABILITY_ICON_OFFSET) + 1;

    private static final String TYPE_ICON_PREFIX = "textures/goo/type/";
    private static final String ABILITY_ICON_PREFIX = "textures/goo/ability/";
    private static final String ICON_SUFFIX = ".png";
    private static final char NAMESPACE_SEPARATOR = ':';
    private static final String BADGE_ICON_PREFIX = "textures/goo/badge/";
    private static final String WORD_SEPARATOR = "\\s+";


    private RadialWheelRenderer() {
    }

    /**
     * What one frame of the wheel draws from.
     *
     * @param wheel     the wheel's state
     * @param types     the types, one per petal at rest
     * @param abilities the abilities each type opens to, by type index, each with its locked flag
     * @param available the amount the player holds per type, snapshot on open
     * @param centerX   the wheel's center x
     * @param centerY   the wheel's center y
     * @param radius    the wheel's outer radius
     * @param look        the fluid, edge and colors a petal draws with
     * @param partialTick the fraction of a tick since the last one, for the petals' ease
     */
    record Frame(RadialWheel wheel, List<ResourceKey<GooTypeDefinition>> types,
                 List<List<OfferedAbility>> abilities, Map<ResourceKey<GooTypeDefinition>, Integer> available,
                 int centerX, int centerY, int radius, PetalLook look, float partialTick) {
    }

    /**
     * Draws the hub, every petal of the ring and the center label.
     *
     * @param graphics the GUI graphics extractor
     * @param font     the font
     * @param frame    what the frame draws from
     * @return every required item icon the locked petals drew, for the cursor to name
     */
    static List<ItemIcon> render(GuiGraphicsExtractor graphics, Font font, Frame frame) {
        blitMask(graphics, frame, frame.look().hubMask(), HUB_COLOR);
        List<Words> words = new ArrayList<>();
        for (RadialWheel.PetalArc petal : frame.wheel().displayedLayout(frame.partialTick())) {
            if (petal.isAbility()) {
                words.add(renderAbility(graphics, frame, petal));
            } else {
                renderType(graphics, frame, petal);
            }
        }
        // abilities-replace-the-hovered-type: words draw last, on a layer nothing draws over
        List<ItemIcon> required = new ArrayList<>();
        for (Words ability : words) {
            drawWords(graphics, font, ability);
            required.addAll(ability.required());
        }
        slashLearnedItems(graphics, required);
        renderCenterLabel(graphics, font, frame);
        return required;
    }

    /**
     * Draws a type's petal with its icon alone, shrunken or at rest.
     * decision abilities-replace-the-hovered-type
     *
     * @param graphics the GUI graphics extractor
     * @param frame    what the frame draws from
     * @param petal    the type's petal
     */
    private static void renderType(GuiGraphicsExtractor graphics, Frame frame, RadialWheel.PetalArc petal) {
        ResourceKey<GooTypeDefinition> key = frame.types().get(petal.type());
        boolean selected = petal.type() == frame.wheel().selectedType();
        PetalPainter.paint(graphics, frame, frame.look().fluidFace(key), petal,
                computeOverlayTint(selected, frame.available().getOrDefault(key, 0) <= 0));
        blitIcon(graphics, new Icon(typeIcon(key), TYPE_ICON_SIZE), lengthCenter(frame, petal),
                COLOR_WHITE);
    }

    /**
     * The screen point dead center along a type petal's length, on its
     * center line halfway from the hub to its end, at rest or receded.
     * decision abilities-replace-the-hovered-type
     *
     * @param frame what the frame draws from
     * @param petal the type petal
     * @return the point's x and y
     */
    static int[] lengthCenter(Frame frame, RadialWheel.PetalArc petal) {
        PetalMask.Point center = PetalMask.Point.polar(petal.center(),
                (RadialWheel.HUB_FRACTION + petal.length()) / HALF);
        return new int[]{frame.centerX() + (int) Math.round(center.x() * frame.radius()),
                frame.centerY() + (int) Math.round(center.y() * frame.radius())};
    }

    /**
     * Draws an ability petal of the open type and answers what it shows
     * after every petal: an unlocked petal's icon at its tip and its words, or
     * a locked petal's required items alone, in a column along its center
     * line so each stays inside the petal.
     * decision abilities-replace-the-hovered-type
     * decision locked-petal-lists-the-unlearned-items
     *
     * @param graphics the GUI graphics extractor
     * @param frame    what the frame draws from
     * @param petal    the ability's petal
     * @return the ability's words or items and where they go
     */
    private static Words renderAbility(GuiGraphicsExtractor graphics, Frame frame, RadialWheel.PetalArc petal) {
        ResourceKey<GooTypeDefinition> key = frame.types().get(petal.type());
        OfferedAbility offered = frame.abilities().get(petal.type()).get(petal.ability());
        ClientAbility ability = offered.ability();
        boolean hovered = petal.ability() == frame.wheel().hoveredAbility();
        FanSlot slotLabels = fanSlot(ability, frame.available().getOrDefault(key, 0));
        // locked-petal-stays-on-the-wheel: a locked petal takes the unaffordable petal's dimmed look
        boolean dimmed = offered.locked() || slotLabels.dimmed();
        PetalPainter.paint(graphics, frame, frame.look().fluidFace(key), petal,
                computeOverlayTint(hovered, dimmed));
        int[] restingTip = tipCenter(frame, petal);
        if (offered.locked()) {
            return new Words(List.of(), null, restingTip, DISABLED_TEXT_COLOR, requiredIcons(frame, petal, offered));
        }
        // icons-slide-in-from-behind-the-tip: the icon rides in while the words stay on the resting tip
        blitAbilityIcon(graphics, frame, petal, ability, dimmed ? computeOverlayTint(false, true) : COLOR_WHITE);
        int textColor = dimmed ? DISABLED_TEXT_COLOR : hovered ? HOVER_TEXT_COLOR : COLOR_WHITE;
        return new Words(splitNameLines(buildLabel(ability).getString()), Component.literal(slotLabels.costLabel()),
                restingTip, textColor, List.of());
    }

    /**
     * A locked petal's item icons, one per required item in the order the
     * ability names them, laid along the petal's center line.
     * decision locked-petal-lists-the-unlearned-items
     *
     * @param frame   what the frame draws from
     * @param petal   the locked petal
     * @param offered the locked ability and its required items
     * @return each item's icon, from the hub outward
     */
    private static List<ItemIcon> requiredIcons(Frame frame, RadialWheel.PetalArc petal, OfferedAbility offered) {
        List<OfferedAbility.RequiredItem> required = offered.required();
        List<ItemRect> rects = itemColumn(frame, petal, required.size());
        return IntStream.range(0, rects.size())
                .mapToObj(index -> {
                    OfferedAbility.RequiredItem item = required.get(index);
                    return new ItemIcon(item.item(), frame.look().itemStack(item.item()), rects.get(index),
                            item.learned());
                })
                .toList();
    }

    /**
     * What an ability petal shows after every petal: its name's lines and
     * cost gathered around its icon, or on a locked petal its item icons alone.
     *
     * @param name       the name's lines, none on a locked petal
     * @param cost       the first-throw cost, null on a locked petal
     * @param restingTip the point the icon rests on once the petal is fanned, which the words gather around
     * @param color      the words' color
     * @param required   the required items' icons, empty on an unlocked petal
     */
    private record Words(List<Component> name, @Nullable Component cost, int[] restingTip, int color,
                         List<ItemIcon> required) {
    }

    /**
     * A 16x16 screen square one item icon draws in.
     *
     * @param left the square's left edge
     * @param top  the square's top edge
     */
    record ItemRect(int left, int top) {

        /**
         * Whether a screen point falls inside the square.
         *
         * @param x the point's x
         * @param y the point's y
         * @return true when the point is on the square
         */
        boolean contains(double x, double y) {
            return x >= left && x < left + ITEM_ICON_SIZE && y >= top && y < top + ITEM_ICON_SIZE;
        }
    }

    /**
     * One required item a locked petal draws: its id, its stack, where, and
     * whether the player has learned it, which crosses it off.
     * decision locked-petal-lists-the-unlearned-items
     *
     * @param item    the item's id
     * @param stack   the item's stack, which draws and names it
     * @param rect    where it draws
     * @param learned true when the player knows the item
     */
    record ItemIcon(Identifier item, ItemStack stack, ItemRect rect, boolean learned) {
    }

    /**
     * Lays out a locked petal's items in a column along the petal's center
     * line, one square per item at a fixed stride from the hub outward,
     * the column centered halfway along the petal so every square stays
     * inside it and the cursor over one keeps the petal hovered.
     * decision locked-petal-lists-the-unlearned-items
     *
     * @param frame what the frame draws from
     * @param petal the locked petal
     * @param count how many items the column holds
     * @return each item's square, from the hub outward
     */
    static List<ItemRect> itemColumn(Frame frame, RadialWheel.PetalArc petal, int count) {
        PetalMask.Petal shape = petal.shape();
        double middle = (shape.inner() + shape.outer()) / HALF * frame.radius();
        double firstOffset = (count - 1) * ITEM_STRIDE / (double) HALF;
        PetalMask.Point along = PetalMask.Point.polar(petal.center(), 1.0);
        // icons-slide-in-from-behind-the-tip: the column rides in by the icon's offset
        double slide = slideDistance(frame, petal);
        return IntStream.range(0, count).mapToObj(index -> {
            double distance = middle - firstOffset + index * ITEM_STRIDE + slide;
            int x = frame.centerX() + (int) Math.round(along.x() * distance);
            int y = frame.centerY() + (int) Math.round(along.y() * distance);
            return new ItemRect(x - ITEM_ICON_SIZE / HALF, y - ITEM_ICON_SIZE / HALF);
        }).toList();
    }

    /**
     * The item whose icon lies under the cursor, so its name can show.
     * decision locked-petal-lists-the-unlearned-items
     *
     * @param icons   every item icon the frame drew
     * @param cursorX the cursor's x
     * @param cursorY the cursor's y
     * @return the icon under the cursor, or null over none
     */
    static @Nullable ItemIcon itemUnder(List<ItemIcon> icons, double cursorX, double cursorY) {
        return icons.stream().filter(icon -> icon.rect().contains(cursorX, cursorY)).findFirst().orElse(null);
    }

    /**
     * The screen point at the center of a petal's round tip, where its icon
     * and words have room to breathe.
     * decision abilities-replace-the-hovered-type
     *
     * @param frame what the frame draws from
     * @param petal the petal
     * @return the point's x and y
     */
    static int[] tipCenter(Frame frame, RadialWheel.PetalArc petal) {
        PetalMask.Point tip = petal.shape().tipCenter();
        return new int[]{frame.centerX() + (int) Math.round(tip.x() * frame.radius()),
                frame.centerY() + (int) Math.round(tip.y() * frame.radius())};
    }

    /**
     * The screen point an ability petal's icon centers on as its type fans
     * out: past the petal's outer radius while the type is closed, far enough
     * that the icon and the badge beside it lie wholly outside the petal,
     * sliding inward along the center line to the tip's center once the type
     * is fully open, and back out along the same line as it closes.
     * decision icons-slide-in-from-behind-the-tip
     *
     * @param frame what the frame draws from
     * @param petal the ability petal
     * @return the point's x and y
     */
    static int[] contentCenter(Frame frame, RadialWheel.PetalArc petal) {
        PetalMask.Point tip = petal.shape().tipCenter();
        PetalMask.Point outward = PetalMask.Point.polar(petal.center(), slideDistance(frame, petal));
        return new int[]{frame.centerX() + (int) Math.round(tip.x() * frame.radius() + outward.x()),
                frame.centerY() + (int) Math.round(tip.y() * frame.radius() + outward.y())};
    }

    /**
     * How many pixels outward along its center line an ability petal's
     * content sits from where it rests: none at full openness, at none the
     * distance that puts the icon's center {@link #ENTRY_REACH} past the
     * petal's outer radius.
     * decision icons-slide-in-from-behind-the-tip
     *
     * @param frame what the frame draws from
     * @param petal the ability petal
     * @return the distance in pixels
     */
    static double slideDistance(Frame frame, RadialWheel.PetalArc petal) {
        PetalMask.Petal shape = petal.shape();
        PetalMask.Point tip = shape.tipCenter();
        double fullSlide = shape.outer() * frame.radius() + ENTRY_REACH
                - Math.hypot(tip.x(), tip.y()) * frame.radius();
        return (1.0 - petal.openness()) * fullSlide;
    }

    /**
     * Draws the ability's icon, then its badge untinted directly to the icon's
     * right, where the words never clip it and it never covers the icon, each
     * cut at the petal's border so only the part under its face shows.
     * badge-marks-the-target-kind
     * decision icons-slide-in-from-behind-the-tip
     *
     * @param graphics the GUI graphics extractor
     * @param frame    what the frame draws from
     * @param petal    the ability's petal
     * @param ability  the synced ability
     * @param color    the wedge's tint for the icon
     */
    private static void blitAbilityIcon(GuiGraphicsExtractor graphics, Frame frame, RadialWheel.PetalArc petal,
                                        ClientAbility ability, int color) {
        int[] slot = contentCenter(frame, petal);
        PetalMask.Petal shape = petal.shape();
        PetalPainter.paintSprite(graphics, frame, shape, frame.look().sprite(resolveAbilityIcon(ability)), slot,
                ABILITY_ICON_SIZE, color);
        PetalPainter.paintSprite(graphics, frame, shape, frame.look().sprite(badgeIcon(ability.badge())),
                badgeBeside(slot), ABILITY_ICON_SIZE, COLOR_WHITE);
    }

    /**
     * The point a badge centers on: one icon's width right of the ability
     * icon's center, on its row, so the badge sits beside the icon edge to edge.
     * badge-marks-the-target-kind
     *
     * @param iconCenter the ability icon's center
     * @return the badge's center
     */
    private static int[] badgeBeside(int[] iconCenter) {
        return new int[]{iconCenter[0] + ABILITY_ICON_SIZE, iconCenter[1]};
    }

    /**
     * The sprite that marks a badge kind.
     * badge-marks-the-target-kind
     *
     * @param badge the ability's declared target kind
     * @return the badge texture under textures/goo/badge/
     */
    static Identifier badgeIcon(AbilityBadge badge) {
        return Identifier.fromNamespaceAndPath(Goo.MODID, BADGE_ICON_PREFIX + badge.getSerializedName() + ICON_SUFFIX);
    }


    /**
     * Breaks a resolved ability name on its spaces into one line per word,
     * so a two-term name fits the petal's width.
     * petal-label-wraps-two-terms
     *
     * @param name the ability's resolved name
     * @return one line per word, a one-word name answering one line
     */
    static List<Component> splitNameLines(String name) {
        return Arrays.stream(name.trim().split(WORD_SEPARATOR))
                .map(word -> (Component) Component.literal(word))
                .toList();
    }

    /**
     * Draws a line of words centered on a point with a dark border all the
     * way around, so light words stand out against the brightest goos: the
     * line eight times one pixel off in each direction in the border's color,
     * then once on top in its own, the way glowing signs outline theirs.
     * decision abilities-replace-the-hovered-type
     *
     * @param graphics the GUI graphics extractor
     * @param font     the font
     * @param line     the words
     * @param centerX  the x the line centers on
     * @param y        the line's top
     * @param color    the words' color
     */
    static void outlinedText(GuiGraphicsExtractor graphics, Font font, Component line, int centerX, int y, int color) {
        int left = centerX - font.width(line) / HALF;
        for (int dx = -OUTLINE_WIDTH; dx <= OUTLINE_WIDTH; dx++) {
            for (int dy = -OUTLINE_WIDTH; dy <= OUTLINE_WIDTH; dy++) {
                if (dx != 0 || dy != 0) {
                    graphics.text(font, line, left + dx, y + dy, OUTLINE_COLOR, false);
                }
            }
        }
        graphics.text(font, line, left, y, color, false);
    }

    /**
     * Draws an ability's name centered directly above its icon and its cost
     * centered directly below it, on screen.
     * decision abilities-replace-the-hovered-type
     *
     * @param graphics the GUI graphics extractor
     * @param font     the font
     * @param words    the ability's words and their icon
     */
    private static void drawWords(GuiGraphicsExtractor graphics, Font font, Words words) {
        int x = words.restingTip()[0];
        int nameY = words.restingTip()[1] - ABILITY_ICON_OFFSET - LABEL_GAP - words.name().size() * font.lineHeight;
        for (Component line : words.name()) {
            outlinedText(graphics, font, line, x, nameY, words.color());
            nameY += font.lineHeight;
        }
        if (words.cost() != null) {
            outlinedText(graphics, font, words.cost(), x, words.restingTip()[1] + ABILITY_ICON_OFFSET + LABEL_GAP,
                    words.color());
        }
        for (ItemIcon icon : words.required()) {
            graphics.item(icon.stack(), icon.rect().left(), icon.rect().top());
        }
    }

    /**
     * Crosses off each learned item with a red slash from its square's
     * lower left to its upper right, on a stratum above the item renders so
     * the slash reads over the item.
     * decision locked-petal-lists-the-unlearned-items
     *
     * @param graphics the GUI graphics extractor
     * @param icons    every required item icon the frame drew
     */
    private static void slashLearnedItems(GuiGraphicsExtractor graphics, List<ItemIcon> icons) {
        if (icons.stream().noneMatch(ItemIcon::learned)) {
            return;
        }
        graphics.nextStratum();
        for (ItemIcon icon : icons) {
            if (!icon.learned()) {
                continue;
            }
            int bottom = icon.rect().top() + ITEM_ICON_SIZE;
            for (int step = 0; step < ITEM_ICON_SIZE; step++) {
                int x = icon.rect().left() + step;
                int y = bottom - step;
                graphics.fill(x, Math.max(icon.rect().top(), y - SLASH_THICKNESS), x + 1, y, SLASH_COLOR);
            }
        }
    }

    /**
     * What an ability petal of the open type reads: its first-throw cost, dimmed
     * when it exceeds the type's holdings (decision radial-shows-first-throw-cost-and-holdings).
     *
     * @param costLabel the first-throw cost, formatted
     * @param dimmed    true when the first throw costs more than the holdings
     */
    record FanSlot(String costLabel, boolean dimmed) {
    }

    /**
     * Reads an ability wedge's first-throw cost against the type's holdings.
     *
     * @param ability  the synced ability
     * @param holdings the amount the player holds of its type
     * @return the wedge's labels
     */
    static FanSlot fanSlot(ClientAbility ability, int holdings) {
        int firstThrow = ability.cost();
        return new FanSlot(GooFormat.formatAmount(firstThrow), firstThrow > holdings);
    }

    /**
     * The center's holdings line for the selected type.
     *
     * @param holdings the amount the player holds of the type
     * @return the holdings, formatted
     */
    static String holdingsLabel(int holdings) {
        // hud-amounts-read-through-goo-format: the machine panels' goo convention
        return GooFormat.formatAmount(holdings);
    }

    private static void renderCenterLabel(GuiGraphicsExtractor graphics, Font font, Frame frame) {
        RadialWheel wheel = frame.wheel();
        if (!wheel.isOpen()) {
            return;
        }
        ResourceKey<GooTypeDefinition> type = frame.types().get(wheel.selectedType());
        int holdings = frame.available().getOrDefault(type, 0);
        int textColor = holdings <= 0 ? DISABLED_TEXT_COLOR : COLOR_WHITE;
        outlinedText(graphics, font, Component.translatable(GooTypeNames.translationKey(type)),
                frame.centerX(), frame.centerY() - font.lineHeight, textColor);
        outlinedText(graphics, font, Component.literal(holdingsLabel(holdings)),
                frame.centerX(), frame.centerY() + LABEL_GAP, textColor);
    }

    private static void blitMask(GuiGraphicsExtractor graphics, Frame frame, Identifier mask, int color) {
        int size = frame.radius() * HALF;
        graphics.blit(RenderPipelines.GUI_TEXTURED, mask, frame.centerX() - frame.radius(),
                frame.centerY() - frame.radius(), 0.0f, 0.0f, size, size,
                RadialTextures.TEX_SIZE, RadialTextures.TEX_SIZE, RadialTextures.TEX_SIZE, RadialTextures.TEX_SIZE,
                color);
    }

    /**
     * An icon texture and its square size, drawn at the size its source is.
     *
     * @param texture the icon's texture
     * @param size    the source's width and height in pixels, and the drawn size
     */
    private record Icon(Identifier texture, int size) {
    }

    private static void blitIcon(GuiGraphicsExtractor graphics, Icon icon, int[] center, int color) {
        int offset = icon.size() / HALF;
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), center[0] - offset, center[1] - offset,
                0.0f, 0.0f, icon.size(), icon.size(), icon.size(), icon.size(), color);
    }


    private static Identifier typeIcon(ResourceKey<GooTypeDefinition> type) {
        return Identifier.fromNamespaceAndPath(Goo.MODID, TYPE_ICON_PREFIX + GooTypes.id(type) + ICON_SUFFIX);
    }

    /**
     * Computes the tint a wedge's textured mask blits under, so the fluid
     * fill still reads hovered and disabled (decision wedges-render-fluid-texture):
     * a hovered wedge is brighter and more opaque than a resting one, a
     * disabled one darker and dimmer.
     *
     * @param hovered  true for the hovered or selected wedge
     * @param disabled true when the player holds none of it, or cannot afford it
     * @return the packed ARGB tint
     */
    static int computeOverlayTint(boolean hovered, boolean disabled) {
        if (disabled) {
            return ARGB.color(DISABLED_ALPHA, DISABLED_SHADE, DISABLED_SHADE, DISABLED_SHADE);
        }
        return hovered ? ARGB.color(HOVER_ALPHA, COLOR_WHITE) : ARGB.color(NORMAL_ALPHA, REST_SHADE, REST_SHADE, REST_SHADE);
    }

    /**
     * The petal's name label: the bare name, the badge marking the target kind.
     * badge-marks-the-target-kind
     *
     * @param ability the synced ability
     * @return the ability's translatable name
     */
    static Component buildLabel(ClientAbility ability) {
        return Component.translatable(ability.displayName());
    }

    /**
     * Resolves the icon texture for an ability. Uses the explicit override
     * if provided, otherwise falls back to convention path.
     *
     * @param ability the client ability descriptor
     * @return the icon texture identifier
     */
    static Identifier resolveAbilityIcon(ClientAbility ability) {
        if (!ability.icon().isEmpty()) {
            // diagnose-then-fix-radial-icon-id: a namespaced icon keeps its namespace rather than taking a second goo:
            return ability.icon().indexOf(NAMESPACE_SEPARATOR) >= 0
                    ? Identifier.parse(ability.icon())
                    : Identifier.fromNamespaceAndPath(Goo.MODID, ability.icon());
        }
        return Identifier.fromNamespaceAndPath(Goo.MODID, ABILITY_ICON_PREFIX + ability.id().getPath() + ICON_SUFFIX);
    }
}
