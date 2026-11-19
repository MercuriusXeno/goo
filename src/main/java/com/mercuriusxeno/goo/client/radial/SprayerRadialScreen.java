package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.client.network.ClientAbilities;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.SprayerItem;
import com.mercuriusxeno.goo.item.SprayerSetting;
import com.mercuriusxeno.goo.network.SprayerSelectPayload;
import com.mercuriusxeno.goo.type.GooTypeNames;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * The sprayer's wheel, open while the glove menu key is held with the sprayer in hand: a contracted ring of petals,
 * one for each of the canister's sprayable goo types in its own goo, and inside the hub, hugging its edge, the six
 * nozzles across the top and the three flow steps across the bottom, left to right. Clicking a petal or an icon sets
 * that one setting and leaves the wheel open; letting go of the key over one sets it and closes; letting go anywhere
 * else closes. The current nozzle and flow sit on a lit square, and whatever is under the cursor grows and names
 * itself in the middle.
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 * decision spray-item-sprays-more-or-less-per-tick
 * decision spray-item-takes-different-nozzles-doing-different-things
 * decision radial-selects-on-g-release
 * operator ruling 2026-11-18: icons inside the hub, nozzles on top and flow on the bottom, each left to right; the
 * goo petals contract; options can be clicked
 */
public final class SprayerRadialScreen extends Screen {

    private static final int HALF = 2;
    /** Where the goo petals start and end, as fractions of the wheel's radius. */
    private static final double RING_INNER = 0.3;
    private static final double RING_OUTER = 0.46;
    /** An icon's side on screen, its sixteen pixels drawn one to one, and under the cursor. */
    private static final int ICON_SIZE = 16;
    private static final int HOVERED_ICON_SIZE = 20;
    /** How far inside the hub's edge an icon's center sits, past half its side. */
    private static final int ICON_INSET = 3;
    /** How near the cursor must come to an icon's center to rest on it. */
    private static final int ICON_REACH = 10;
    private static final int TEXTURE_SIDE = 16;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int HOVERED = 0xFFFFF29A;
    private static final int RESTING = 0xB0C8C8C8;
    /** The square lit behind the current nozzle and flow. */
    private static final int CURRENT_SQUARE = 0x55FFFFFF;
    private static final String ICON_FOLDER = "textures/gui/sprayer/";
    private static final String FLOW_ICON = "flow_";
    private static final String NOZZLE_ICON = "nozzle_";
    private static final String PNG = ".png";
    /** The type id a pick naming no type sends, leaving the canister's most held sprayable type to spray. */
    private static final String NO_TYPE_PICKED = "";

    private final SprayerWheel model;
    private final List<SprayerWheel.RingPetal> ring;
    private SprayerSetting kept;
    private SprayerWheel.@Nullable RingPetal hovered;

    private SprayerRadialScreen(SprayerWheel model, SprayerSetting kept) {
        super(Component.empty());
        this.model = model;
        this.ring = model.ring();
        this.kept = kept;
    }

    /**
     * Opens the wheel over the held sprayer: its canister's sprayable goo types, none for an empty sprayer, beside
     * the flow steps and the nozzles, which an empty sprayer sets too.
     * operator report 2026-11-18: an empty sprayer opened no wheel
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        ItemStack sprayer = SprayerItem.heldSprayer(player);
        ClientAbilities synced = ClientAbilities.current();
        SprayerWheel model = SprayerWheel.of(CanisterItem.getFluidContent(SprayerItem.fittedCanister(sprayer)),
                type -> synced.forType(type).stream().anyMatch(ability -> ability.badge() == AbilityBadge.RESIDUAL));
        mc.setScreen(new SprayerRadialScreen(model, SprayerItem.settingOf(sprayer)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int radius() {
        return (int) RadialWheel.outerRadius(width, height);
    }

    @Override
    public void mouseMoved(double x, double y) {
        double dx = x - width / (double) HALF;
        double dy = y - height / (double) HALF;
        double distance = Math.hypot(dx, dy) / radius();
        hovered = distance >= RING_INNER && distance <= RING_OUTER ? petalAt(RadialWheel.angleOf(dx, dy))
                : iconAt(x, y);
    }

    private SprayerWheel.@Nullable RingPetal petalAt(double angle) {
        int type = model.gooAt(angle);
        return type == SprayerWheel.NONE ? null : new SprayerWheel.RingPetal(SprayerWheel.Group.GOO, type);
    }

    private SprayerWheel.@Nullable RingPetal iconAt(double x, double y) {
        for (SprayerWheel.RingPetal icon : ring) {
            int[] at = iconCenter(width / HALF, height / HALF, icon);
            if (icon.group() != SprayerWheel.Group.GOO && Math.hypot(x - at[0], y - at[1]) <= ICON_REACH) {
                return icon;
            }
        }
        return null;
    }

    /**
     * Paints the goo petals, the icons around the inside of the hub, and the name of whatever is under the cursor.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float frameDelta) {
        super.extractBackground(graphics, mouseX, mouseY, frameDelta);
        Canvas canvas = new Canvas(width / HALF, height / HALF, radius());
        for (SprayerWheel.RingPetal at : ring) {
            if (at.group() == SprayerWheel.Group.GOO) {
                paintPetal(graphics, canvas, at);
            } else {
                paintIcon(graphics, at);
            }
        }
        if (hovered != null) {
            RadialWheelRenderer.outlinedText(graphics, font, nameOf(hovered), canvas.centerX(),
                    canvas.centerY() - font.lineHeight / HALF, WHITE);
        }
    }

    private void paintPetal(GuiGraphicsExtractor graphics, Canvas canvas, SprayerWheel.RingPetal at) {
        double start = model.gooStartOf(at.index());
        RadialWheel.PetalArc arc = new RadialWheel.PetalArc(at.index(), RadialWheel.NONE, start, model.gooArc(),
                RING_OUTER, null, 0, 1, RING_INNER);
        int tint = at.equals(hovered) ? HOVERED : isCurrent(at) ? WHITE : RESTING;
        PetalPainter.paint(graphics, canvas, PetalLook.LIVE.fluidFace(model.types().get(at.index())), arc, tint);
    }

    private void paintIcon(GuiGraphicsExtractor graphics, SprayerWheel.RingPetal at) {
        int[] center = iconCenter(width / HALF, height / HALF, at);
        if (isCurrent(at)) {
            int half = ICON_SIZE / HALF + 1;
            graphics.fill(center[0] - half, center[1] - half, center[0] + half, center[1] + half, CURRENT_SQUARE);
        }
        int size = at.equals(hovered) ? HOVERED_ICON_SIZE : ICON_SIZE;
        graphics.blit(RenderPipelines.GUI_TEXTURED, iconOf(at), center[0] - size / HALF, center[1] - size / HALF,
                0.0f, 0.0f, size, size, TEXTURE_SIDE, TEXTURE_SIDE, TEXTURE_SIDE, TEXTURE_SIDE, WHITE);
    }

    /**
     * Where an icon's center sits on screen: just inside the hub's edge at its angle, clear of the goo petals.
     *
     * @param centerX the wheel's center, x
     * @param centerY the wheel's center, y
     * @param icon    a nozzle or flow icon
     * @return the icon's center
     */
    private int[] iconCenter(int centerX, int centerY, SprayerWheel.RingPetal icon) {
        double angle = model.hubAngleOf(icon);
        double distance = RING_INNER * radius() - ICON_SIZE / (double) HALF - ICON_INSET;
        return new int[]{centerX + (int) Math.round(Math.sin(angle) * distance),
                centerY - (int) Math.round(Math.cos(angle) * distance)};
    }

    private boolean isCurrent(SprayerWheel.RingPetal at) {
        return switch (at.group()) {
            case GOO -> kept.pickedType().map(model.types().get(at.index())::equals).orElse(at.index() == 0);
            case FLOW -> kept.flow() == model.flows().get(at.index());
            case NOZZLE -> kept.nozzle() == model.nozzles().get(at.index());
        };
    }

    private Identifier iconOf(SprayerWheel.RingPetal at) {
        String name = at.group() == SprayerWheel.Group.FLOW
                ? FLOW_ICON + model.flows().get(at.index()).getSerializedName()
                : NOZZLE_ICON + model.nozzles().get(at.index()).getSerializedName();
        return Identifier.fromNamespaceAndPath(Goo.MODID, ICON_FOLDER + name + PNG);
    }

    private Component nameOf(SprayerWheel.RingPetal at) {
        return switch (at.group()) {
            case GOO -> GooTypeNames.name(model.types().get(at.index()));
            case FLOW -> Component.translatable(model.flows().get(at.index()).langKey());
            case NOZZLE -> Component.translatable(model.nozzles().get(at.index()).langKey());
        };
    }

    /**
     * Sets the clicked petal's or icon's setting and leaves the wheel open for the next.
     *
     * @param event       the click
     * @param doubleClick whether it is a double click
     * @return true where a petal or icon was clicked
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (hovered == null) {
            return super.mouseClicked(event, doubleClick);
        }
        keep(model.picked(kept, hovered));
        return true;
    }

    /**
     * Reads the glove menu key's release, since an open screen takes the keyboard from the mapping.
     *
     * @param event the key release event
     * @return true if the event was handled
     */
    @Override
    public boolean keyReleased(KeyEvent event) {
        if (!GloveRadialKey.MAPPING.matches(event)) {
            return super.keyReleased(event);
        }
        if (hovered != null) {
            keep(model.picked(kept, hovered));
        }
        onClose();
        return true;
    }

    /**
     * Keeps a pick on the held sprayer here and on the server.
     *
     * @param picked the new setting
     */
    private void keep(SprayerSetting picked) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack sprayer = mc.player == null ? ItemStack.EMPTY : SprayerItem.heldSprayer(mc.player);
        if (sprayer.isEmpty()) {
            return;
        }
        kept = picked;
        SprayerItem.keepSetting(sprayer, picked);
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundCustomPayloadPacket(new SprayerSelectPayload(
                    picked.pickedType().map(GooTypes::id).orElse(NO_TYPE_PICKED), picked.flow(), picked.nozzle())));
        }
    }

    /**
     * Where the sprayer's wheel draws.
     *
     * @param centerX the wheel's center, x
     * @param centerY the wheel's center, y
     * @param radius  the wheel's outer radius
     */
    private record Canvas(int centerX, int centerY, int radius) implements PetalCanvas {
    }
}
