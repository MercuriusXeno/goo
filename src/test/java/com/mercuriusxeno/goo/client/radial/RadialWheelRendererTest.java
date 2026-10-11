package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.network.OfferedAbility;
import net.minecraft.world.item.ItemStack;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypeNames;
import com.mercuriusxeno.goo.type.GooTypes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Matrix3x2fStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * Covers RadialWheelRenderer.resolveAbilityIcon over every shipped ability, as the
 * ability sync hands each one to the client (decision diagnose-then-fix-radial-icon-id),
 * and the fan's cost and holdings labels.
 */
class RadialWheelRendererTest {

    private static final String ASSETS_ROOT = "assets/";
    private static final String TEXTURE_PATH = "textures/goo/ability/unstable_timed_bomb.png";

    private static ClientAbility clientAbilityOf(AbilityDefinition definition) {
        return new ClientAbility(definition.id(), definition.displayName(), definition.icon(),
                definition.order(), definition.tags(), definition.behaviors(),
                definition.cost(), definition.delivery(), definition.badge(), definition.requires());
    }

    /**
     * The shipped abilities the radial lists: every one but a tap's drip
     * ability, which the ability sync never sends the glove.
     *
     * @return the radial's shipped abilities
     */
    private static List<ClientAbility> shippedAbilities() {
        return AbilityJson.files().stream()
                .map(AbilityJson::decode)
                .filter(definition -> !definition.hasTag(AbilityTags.TAP))
                .map(RadialWheelRendererTest::clientAbilityOf)
                .toList();
    }

    private static ClientAbility abilityWithIcon(String icon) {
        return new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_timed_bomb"),
                "ability.goo.unstable_timed_bomb", icon, 0, List.of(), List.of(), 0, Delivery.ARC, AbilityBadge.WORLD, List.of());
    }

    /** The badge, not the label, marks the target kind (decision badge-marks-the-target-kind). */
    @Nested
    class Badges {

        @Test
        void entityAbilityLabelReadsTheBareName() {
            ClientAbility entity = new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "hex_charm"),
                    "goo.ability.hex.charm", "", 0, List.of(AbilityTags.ENTITY), List.of(), 0, Delivery.ARC, AbilityBadge.MOB, List.of());

            assertEquals(Component.translatable("goo.ability.hex.charm"), RadialWheelRenderer.buildLabel(entity));
        }

        @Test
        void everyBadgeKindDrawsFromA16x16SpriteUnderGooBadge() {
            assertAll(Arrays.stream(AbilityBadge.values()).map(badge -> (Executable) () -> {
                Identifier sprite = RadialWheelRenderer.badgeIcon(badge);
                assertEquals(Identifier.fromNamespaceAndPath(Goo.MODID,
                        "textures/goo/badge/" + badge.getSerializedName() + ".png"), sprite);
                assertEquals(List.of(RadialWheelRenderer.ABILITY_ICON_SIZE, RadialWheelRenderer.ABILITY_ICON_SIZE),
                        pngDimensions(ASSETS_ROOT + sprite.getNamespace() + "/" + sprite.getPath()), badge.name());
            }));
        }
    }

    @Nested
    class ShippedAbilities {

        @Test
        void everyShippedAbilityResolvesWithoutThrowing() {
            assertAll(shippedAbilities().stream().map(ability ->
                    (Executable) () -> assertDoesNotThrow(() -> RadialWheelRenderer.resolveAbilityIcon(ability),
                            ability.id().toString())));
        }

        @Test
        void everyShippedAbilityIconExistsUnderGooAssets() {
            assertAll(shippedAbilities().stream().map(ability -> (Executable) () -> {
                Identifier icon = RadialWheelRenderer.resolveAbilityIcon(ability);
                assertEquals(Goo.MODID, icon.getNamespace(), ability.id().toString());
                String resource = ASSETS_ROOT + icon.getNamespace() + "/" + icon.getPath();
                assertNotNull(RadialWheelRendererTest.class.getClassLoader().getResource(resource),
                        ability.id() + " resolves " + icon + " but no " + resource + " is on the classpath");
                // ability-icons-read-16x16
                assertEquals(List.of(RadialWheelRenderer.ABILITY_ICON_SIZE, RadialWheelRenderer.ABILITY_ICON_SIZE),
                        pngDimensions(resource), resource);
            }));
        }
    }

    private static final int IHDR_WIDTH_OFFSET = 16;
    private static final int IHDR_END = 24;

    /** Reads a classpath PNG's width and height from its IHDR chunk, which follows the 8-byte signature and the chunk's length and type. */
    static List<Integer> pngDimensions(String resource) throws IOException {
        try (InputStream stream = RadialWheelRendererTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " is not on the classpath");
            ByteBuffer header = ByteBuffer.wrap(stream.readNBytes(IHDR_END));
            return List.of(header.getInt(IHDR_WIDTH_OFFSET), header.getInt(IHDR_WIDTH_OFFSET + Integer.BYTES));
        }
    }

    /** Each petal reads a short name, a two-term one wrapping to a line per word (decisions ability-names-are-one-word, petal-label-wraps-two-terms). */
    @Nested
    class ShortNames {

        private static final String LANG_RESOURCE = "assets/goo/lang/en_us.json";
        private static final Map<String, String> NAME_BY_ABILITY = Map.ofEntries(
                Map.entry("aeon_rewind", "Rewind"), Map.entry("aeon_stasis", "Stasis"),
                Map.entry("aeon_tick", "Tick"), Map.entry("aeon_timekeeper", "Timekeeper"),
                Map.entry("aeon_chronosphere", "Chronosphere"), Map.entry("aeon_haste", "Haste"),
                Map.entry("blaze_ignite", "Scorch"),
                Map.entry("crystal_cloud", "Razor"), Map.entry("crystal_flechettes", "Shards"),
                Map.entry("crystal_prism", "Prism"),
                Map.entry("ender_banish", "Banish"), Map.entry("ender_teleportitis", "Teleportitis"), Map.entry("ender_convoke", "Convoke"), Map.entry("ender_dragon_gate", "End"), Map.entry("ender_oculus", "Oculus"),
                Map.entry("frost_snap", "Snap"), Map.entry("frost_nova", "Nova"), Map.entry("frost_cold", "Cold"), Map.entry("frost_orb", "Orb"), Map.entry("frost_glacial", "Glacial"), Map.entry("frost_iceborn", "Iceborn"), Map.entry("glow_crystal", "Bulb"),
                Map.entry("glow_sunbeam", "Sunbeam"), Map.entry("hex_charm", "Charm"), Map.entry("hex_enchant", "Enchant"), Map.entry("hex_fuse", "Fuse"), Map.entry("hex_spawn", "Spawn"), Map.entry("hex_agitator", "Agitator"), Map.entry("hex_lifetap", "Lifetap"), Map.entry("hex_drain", "Drain"),
                Map.entry("leaf_vines", "Vines"), Map.entry("leaf_bloom", "Bloom"), Map.entry("leaf_growth", "Growth"), Map.entry("leaf_reap", "Reap"), Map.entry("leaf_bio", "Bio"), Map.entry("leaf_verdant", "Verdant"),
                Map.entry("leaf_barkskin", "Barkskin"), Map.entry("metal_spikes", "Urchin"),
                Map.entry("metal_javelin", "Dart"), Map.entry("nether_black_hole", "Anti"),
                Map.entry("nether_decay", "Decay"), Map.entry("nether_hive", "Hive"), Map.entry("nether_undead", "Undead"),
                Map.entry("pulse_zap", "Zap"), Map.entry("pulse_signal", "Signal"),
                Map.entry("pulse_pulser", "Pulser"), Map.entry("pulse_thumper", "Thumper"),
                Map.entry("pulse_metronome", "Metronome"), Map.entry("pulse_relay", "Relay"),
                Map.entry("pulse_extender", "Extender"),
                Map.entry("rock_bore", "Bore"), Map.entry("rock_crush", "Crush"), Map.entry("rock_flatten", "Flatten"),
                Map.entry("glow_scry", "Scry"), Map.entry("glow_lux", "Lux"), Map.entry("glow_radiant", "Radiant"), Map.entry("glow_reflector", "Reflector"),
                Map.entry("rock_petrify", "Petrify"),
                Map.entry("rock_stoneskin", "Stoneskin"),
                Map.entry("shroom_mycosis", "Mycosis"), Map.entry("shroom_colonize", "Spore"),
                Map.entry("shroom_fungal_shift", "Fungal Shift"), Map.entry("shroom_sight", "Sight"),
                Map.entry("typhoon_levitate", "Float"), Map.entry("unstable_timed_bomb", "Countdown"),
                Map.entry("unstable_proximity_mine", "Claymore"),
                Map.entry("blaze_spitfire", "Spitfire"), Map.entry("blaze_kindle", "Kindle"),
                Map.entry("ender_blink", "Blink"), Map.entry("typhoon_propel", "Propel"),
                Map.entry("unstable_explode", "Blast"), Map.entry("weird_slime", "Slime"),
                Map.entry("vital_vitality", "Vitality"), Map.entry("vital_reserve", "Reserve"),
                Map.entry("vital_nourish", "Nourish"), Map.entry("weird_magma", "Magma"), Map.entry("weird_bounce", "Bounce"),
                Map.entry("weird_wobble", "Wobble"));

        private static JsonObject englishLang() throws IOException {
            try (InputStream stream = RadialWheelRendererTest.class.getClassLoader().getResourceAsStream(LANG_RESOURCE)) {
                assertNotNull(stream, LANG_RESOURCE + " is not on the classpath");
                return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }

        @Test
        void everyShippedAbilityReadsItsShortName() throws IOException {
            JsonObject lang = englishLang();
            List<ClientAbility> shipped = shippedAbilities();
            assertEquals(NAME_BY_ABILITY.keySet(),
                    shipped.stream().map(ability -> ability.id().getPath()).collect(Collectors.toSet()));
            assertAll(shipped.stream().map(ability -> (Executable) () -> assertEquals(
                    NAME_BY_ABILITY.get(ability.id().getPath()),
                    lang.get(ability.displayName()).getAsString(), ability.id().toString())));
        }

        @Test
        void twoWordNameSplitsIntoALinePerWord() {
            assertEquals(List.of("Frost", "Disc"), RadialWheelRenderer.splitNameLines("Frost Disc").stream()
                    .map(Component::getString).toList());
        }

        @Test
        void oneWordNameStaysOneLine() {
            assertEquals(List.of("Stasis"), RadialWheelRenderer.splitNameLines("Stasis").stream()
                    .map(Component::getString).toList());
        }
    }

    /** The fan reads each ability's first-throw cost and the type's holdings (decision radial-shows-first-throw-cost-and-holdings). */
    @Nested
    class CostAndHoldings {

        private static final int HOLDINGS = 1500;
        private static final int AFFORDABLE = 1000;
        private static final int UNAFFORDABLE = 2000;
        private static final int SIXTEEN_GOO = 16_000;

        private static ClientAbility costing(int firstThrow) {
            return new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "cost_" + firstThrow),
                    "ability.goo.cost", "", 0, List.of(), List.of(), firstThrow, Delivery.ARC, AbilityBadge.WORLD, List.of());
        }

        @Test
        void wedgeReadsItsFirstThrowCost() {
            ClientAbility cheap = costing(AFFORDABLE);
            ClientAbility dear = costing(UNAFFORDABLE);

            assertEquals("1K", RadialWheelRenderer.fanSlot(cheap, HOLDINGS).costLabel());
            assertEquals("2K", RadialWheelRenderer.fanSlot(dear, HOLDINGS).costLabel());
        }

        @Test
        void centerReadsTheTypesHoldings() {
            assertEquals("1.5K", RadialWheelRenderer.holdingsLabel(HOLDINGS));
            assertEquals("16K", RadialWheelRenderer.holdingsLabel(SIXTEEN_GOO));
        }

        @Test
        void wedgeCostingMoreThanTheHoldingsReadsDimmed() {
            assertFalse(RadialWheelRenderer.fanSlot(costing(AFFORDABLE), HOLDINGS).dimmed());
            assertTrue(RadialWheelRenderer.fanSlot(costing(UNAFFORDABLE), HOLDINGS).dimmed());
        }

        @Test
        void wedgeCostingExactlyTheHoldingsReadsBright() {
            assertFalse(RadialWheelRenderer.fanSlot(costing(HOLDINGS), HOLDINGS).dimmed());
        }

        /** A held effect reads its upkeep a second, dimmed short of a second's worth (decision self-effects-trickle-until-ended). */
        @Test
        void heldWedgeReadsItsUpkeepPerSecond() {
            ClientAbility held = new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "held"), "ability.goo.held",
                    "", 0, List.of(), List.of(), 0, Delivery.of(DeliveryKind.SELF), AbilityBadge.BREW, List.of(),
                    AbilityArea.NONE, IndicatorShowing.HELD, List.of(), 1);
            assertEquals("20/s", RadialWheelRenderer.fanSlot(held, HOLDINGS).costLabel());
            assertFalse(RadialWheelRenderer.fanSlot(held, 20).dimmed());
            assertTrue(RadialWheelRenderer.fanSlot(held, 19).dimmed());
        }
    }

    /**
     * A frame with a type open draws shrunken type petals with no text, each
     * ability petal with its name and cost, and the open type's name and
     * holdings in the hub (decision abilities-replace-the-hovered-type).
     */
    @Nested
    class OpenTypeFrame {

        private static final int TYPES = 16;
        private static final int OPEN_TYPE = 3;
        private static final int ABILITIES = 4;
        private static final int HOLDINGS = 1500;
        private static final int CENTER_X = 1200;
        private static final int CENTER_Y = 1100;
        private static final int RADIUS = 1000;
        private static final int LINES_PER_ABILITY = 2;
        /** An ability index no petal holds, so a frame rendered with it locks nothing. */
        private static final int NO_ABILITY = -1;
        /** The open type's ability a locked frame locks. */
        private static final int LOCKED_ABILITY = 1;
        /** The items the locked ability requires: glass learned, sand and clay not. */
        private static final List<OfferedAbility.RequiredItem> REQUIRED = List.of(
                new OfferedAbility.RequiredItem(Identifier.withDefaultNamespace("glass"), true),
                new OfferedAbility.RequiredItem(Identifier.withDefaultNamespace("sand"), false),
                new OfferedAbility.RequiredItem(Identifier.withDefaultNamespace("clay"), false));
        /** How far, in pixels, an item's center may stray from the petal's center line or its stride. */
        private static final double COLUMN_TOLERANCE = 1.5;

        private static final PetalLook.SpriteBox SPRITE = new PetalLook.SpriteBox(0.25f, 0.5f, 0.125f, 0.25f);
        private static final int EDGE_COLOR = 0xFF123456;
        private static final String BAKED_PETAL_PATH = "dynamic/radial_arc_";
        /** How near a grid line a corner sits to read either edge of the sprite, as a fraction of a tile. */
        private static final double TILE_TOLERANCE = 1e-3;
        /** How far a sampled UV may stray from the expected, in atlas units: float rounding of screen positions. */
        private static final double SAMPLE_TOLERANCE = 1e-3;
        /** How far, in pixels, an icon's center may sit from its tip's center: integer pixel rounding. */
        private static final double TIP_TOLERANCE = 2.0;
        /** Every line's width in pixels, as the stubbed font measures it. */
        private static final int WORD_WIDTH = 40;
        /** The font's line height, which a mocked Font keeps from its declaration. */
        private static final int LINE_HEIGHT = 9;
        /** A type icon's side, as the renderer draws it. */
        private static final int TYPE_ICON_SIZE = 11;
        /** Where a text call's ARGB color sits among its arguments. */
        private static final int TEXT_COLOR_ARGUMENT = 4;
        /** Where a blit's ARGB color sits among its arguments. */
        private static final int ICON_COLOR_ARGUMENT = 10;

        private final TextureSetup atlas = TextureSetup.singleTexture(mock(GpuTextureView.class),
                mock(GpuSampler.class));
        /** Each whole-file sprite the frame bound, by its texture, so a submission names the sprite it draws. */
        private final Map<Identifier, TextureSetup> sprites = new java.util.HashMap<>();

        /** An atlas, a sprite box and colors with no client behind them, so the frame renders off the game. */
        private final PetalLook fakeLook = new PetalLook() {
            @Override
            public TextureSetup sprite(Identifier texture) {
                return sprites.computeIfAbsent(texture, unused -> TextureSetup.singleTexture(
                        mock(GpuTextureView.class), mock(GpuSampler.class)));
            }

            @Override
            public FluidFace fluidFace(ResourceKey<GooTypeDefinition> type) {
                return new FluidFace(atlas, SPRITE, 0xFFFFFFFF, EDGE_COLOR);
            }

            @Override
            public Identifier hubMask() {
                return Identifier.fromNamespaceAndPath("gootest", "hub");
            }

            @Override
            public ItemStack itemStack(Identifier item) {
                return mock(ItemStack.class);
            }
        };

        /** One line of words the frame drew in its own color: where it centered, its top and what. */
        private record DrawnText(int x, int y, Component text) {
        }

        private final List<ResourceKey<GooTypeDefinition>> types = IntStream.range(0, TYPES)
                .mapToObj(type -> ResourceKey.create(GooTypes.REGISTRY,
                        Identifier.fromNamespaceAndPath(Goo.MODID, "type_" + type)))
                .toList();

        private RadialWheel openWheel() {
            RadialWheel wheel = new RadialWheel(TYPES, type -> ABILITIES);
            double angle = (OPEN_TYPE + 0.5) * wheel.typeArc();
            wheel.moveCursor(Math.sin(angle) * RADIUS * 0.6, -Math.cos(angle) * RADIUS * 0.6, RADIUS);
            for (int tick = 0; tick < RingEase.DURATION_TICKS; tick++) {
                wheel.tick();
            }
            return wheel;
        }

        private GuiGraphicsExtractor renderFrame(RadialWheel wheel) {
            return renderFrame(wheel, NO_ABILITY);
        }

        /**
         * Renders the frame with one ability of the open type locked.
         *
         * @param wheel         the wheel's state
         * @param lockedAbility the open type's ability index to lock, or {@link #NO_ABILITY}
         * @return the mocked graphics the frame drew to
         */
        private GuiGraphicsExtractor renderFrame(RadialWheel wheel, int lockedAbility) {
            List<List<OfferedAbility>> abilities = IntStream.range(0, TYPES)
                    .mapToObj(type -> IntStream.range(0, ABILITIES).mapToObj(ability -> new OfferedAbility(
                            new ClientAbility(
                            Identifier.fromNamespaceAndPath("gootest", "ability_" + type + "_" + ability),
                            "ability.gootest.word", "", 0, List.of(), List.of(), 0, Delivery.ARC, AbilityBadge.WORLD, List.of()),
                            type == OPEN_TYPE && ability == lockedAbility ? REQUIRED : List.of()))
                            .toList())
                    .toList();
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            when(graphics.pose()).thenReturn(new Matrix3x2fStack(1));
            Font font = mock(Font.class);
            when(font.width(any(FormattedText.class))).thenReturn(WORD_WIDTH);
            RadialWheelRenderer.render(graphics, font, frameOf(wheel, abilities));
            return graphics;
        }

        private RadialWheelRenderer.Frame frameOf(RadialWheel wheel, List<List<OfferedAbility>> abilities) {
            return new RadialWheelRenderer.Frame(wheel, types, abilities,
                    Map.of(types.get(OPEN_TYPE), HOLDINGS), CENTER_X, CENTER_Y, RADIUS, fakeLook, 0.0f);
        }

        /** Whether a screen point lies within a petal's angular span and between its inner and outer radius. */
        private static boolean insidePetal(RadialWheel.PetalArc petal, double x, double y) {
            double distance = Math.hypot(x - CENTER_X, y - CENTER_Y) / RADIUS;
            double offset = RadialWheel.angleOf(x - CENTER_X, y - CENTER_Y) - petal.start();
            double wrapped = (offset % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
            return wrapped < petal.arc() && distance > petal.shape().inner() && distance < petal.shape().outer();
        }

        /** Whether all four corners of an item's square lie inside a petal. */
        private static boolean squareInsidePetal(RadialWheel.PetalArc petal, RadialWheelRenderer.ItemRect rect) {
            int size = RadialWheelRenderer.ITEM_ICON_SIZE;
            return insidePetal(petal, rect.left(), rect.top()) && insidePetal(petal, rect.left() + size, rect.top())
                    && insidePetal(petal, rect.left(), rect.top() + size)
                    && insidePetal(petal, rect.left() + size, rect.top() + size);
        }

        /** Whether a screen corner lies on an item's square, its far edges included, within float rounding. */
        private static boolean onSquare(RadialWheelRenderer.ItemRect rect, PetalRenderState.ScreenVertex corner) {
            double slack = 1e-3;
            int size = RadialWheelRenderer.ITEM_ICON_SIZE;
            return corner.x() >= rect.left() - slack && corner.x() <= rect.left() + size + slack
                    && corner.y() >= rect.top() - slack && corner.y() <= rect.top() + size + slack;
        }

        /**
         * An item square straddling a locked petal's border draws only its
         * part under the petal's face, and the cursor still names the item
         * anywhere on its whole square.
         * decision icons-slide-in-from-behind-the-tip
         */
        @Test
        void itemStraddlingTheBorderCutsAtItAndStillNamesItselfAcrossItsSquare() {
            RadialWheel wheel = openWheel();
            RadialWheelRenderer.Frame frame = frameOf(wheel, List.of());
            RadialWheel.PetalArc petal = wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .findFirst().orElseThrow();
            PetalMask.Point edge = PetalMask.Point.polar(petal.center(), petal.shape().outer());
            int half = RadialWheelRenderer.ITEM_ICON_SIZE / 2;
            RadialWheelRenderer.ItemRect rect = new RadialWheelRenderer.ItemRect(
                    (int) Math.round(CENTER_X + edge.x() * RADIUS) - half,
                    (int) Math.round(CENTER_Y + edge.y() * RADIUS) - half);
            Identifier glass = Identifier.withDefaultNamespace("glass");
            RadialWheelRenderer.ItemIcon icon = new RadialWheelRenderer.ItemIcon(glass, mock(ItemStack.class), rect,
                    false, petal.shape());

            List<PetalRenderState.ScreenVertex> cut = PetalPainter.itemCut(frame, icon);

            assertFalse(cut.isEmpty());
            assertAll(cut.stream().map(corner -> (Executable) () -> {
                assertTrue(PetalMeshTest.insideWithSlack(petal.shape(), (corner.x() - CENTER_X) / (double) RADIUS,
                        (corner.y() - CENTER_Y) / (double) RADIUS), corner + " off the petal");
                assertTrue(onSquare(rect, corner), corner + " off the square");
            }));
            double kept = 0;
            for (int quad = 0; quad < cut.size(); quad += 4) {
                double twice = 0;
                for (int i = 0; i < 4; i++) {
                    PetalRenderState.ScreenVertex from = cut.get(quad + i);
                    PetalRenderState.ScreenVertex to = cut.get(quad + (i + 1) % 4);
                    twice += from.x() * to.y() - to.x() * from.y();
                }
                kept += Math.abs(twice) / 2;
            }
            int whole = RadialWheelRenderer.ITEM_ICON_SIZE * RadialWheelRenderer.ITEM_ICON_SIZE;
            assertTrue(kept > 0 && kept < whole, "kept " + kept + " of " + whole);
            double far = RadialWheelRenderer.ITEM_ICON_SIZE - 0.5;
            assertAll(List.of(new double[]{0, 0}, new double[]{far, 0}, new double[]{0, far}, new double[]{far, far})
                    .stream().map(offset -> (Executable) () -> assertEquals(glass, RadialWheelRenderer.itemUnder(
                            List.of(icon), rect.left() + offset[0], rect.top() + offset[1]).item(),
                            "corner " + offset[0] + "," + offset[1])));
        }

        private static double[] centerOf(RadialWheelRenderer.ItemRect rect) {
            return new double[]{rect.left() + RadialWheelRenderer.ITEM_ICON_SIZE / 2.0,
                    rect.top() + RadialWheelRenderer.ITEM_ICON_SIZE / 2.0};
        }

        private List<DrawnText> renderTexts(RadialWheel wheel) {
            return wordsDrawn(renderFrame(wheel), color -> color != RadialWheelRenderer.OUTLINE_COLOR);
        }

        /** The text calls a frame made in the colors a test asks for, each read back to its center. */
        private static List<DrawnText> wordsDrawn(GuiGraphicsExtractor graphics,
                                                  java.util.function.IntPredicate color) {
            return mockingDetails(graphics).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("text")
                            && color.test(call.getArgument(TEXT_COLOR_ARGUMENT)))
                    .map(call -> new DrawnText((int) call.getArgument(2) + WORD_WIDTH / 2, call.getArgument(3),
                            call.getArgument(1)))
                    .toList();
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void everyLineDrawsInsideADarkBorderAllTheWayAround() {
            GuiGraphicsExtractor graphics = renderFrame(openWheel());
            List<DrawnText> lines = wordsDrawn(graphics, color -> color != RadialWheelRenderer.OUTLINE_COLOR);
            List<DrawnText> border = wordsDrawn(graphics, color -> color == RadialWheelRenderer.OUTLINE_COLOR);

            assertFalse(lines.isEmpty());
            assertAll(lines.stream().map(line -> (Executable) () -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int x = line.x() + dx;
                        int y = line.y() + dy;
                        boolean expected = dx != 0 || dy != 0;
                        assertEquals(expected, border.stream().anyMatch(edge -> edge.x() == x && edge.y() == y
                                && edge.text().equals(line.text())), line + " border at " + dx + "," + dy);
                    }
                }
            }));
        }

        private <T> List<T> submittedArguments(GuiGraphicsExtractor graphics, String method, Class<T> type) {
            return mockingDetails(graphics).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals(method))
                    .flatMap(call -> Arrays.stream(call.getArguments()))
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        private static boolean isInHub(DrawnText text) {
            return Math.hypot(text.x() - CENTER_X, text.y() - CENTER_Y) < RadialWheel.HUB_FRACTION * RADIUS;
        }

        private static RadialWheel.PetalArc petalUnder(RadialWheel wheel, DrawnText text) {
            return RadialWheel.petalAt(wheel.layout(), RadialWheel.angleOf(text.x() - CENTER_X, text.y() - CENTER_Y));
        }

        @Test
        void hubDrawsTheOpenTypesNameAndHoldings() {
            List<Component> center = renderTexts(openWheel()).stream()
                    .filter(OpenTypeFrame::isInHub)
                    .map(DrawnText::text)
                    .toList();

            assertEquals(List.of(Component.translatable(GooTypeNames.translationKey(types.get(OPEN_TYPE))),
                    Component.literal(RadialWheelRenderer.holdingsLabel(HOLDINGS))), center);
        }

        @Test
        void eachAbilityPetalDrawsTwoLinesAndNoTypePetalDrawsText() {
            RadialWheel wheel = openWheel();
            Map<RadialWheel.PetalArc, Long> linesByPetal = renderTexts(wheel).stream()
                    .filter(text -> !isInHub(text))
                    .collect(Collectors.groupingBy(text -> petalUnder(wheel, text), Collectors.counting()));

            assertTrue(linesByPetal.keySet().stream().allMatch(RadialWheel.PetalArc::isAbility),
                    "text drawn on a type petal: " + linesByPetal);
            assertAll(wheel.layout().stream().filter(RadialWheel.PetalArc::isAbility).map(petal -> (Executable) () ->
                    assertEquals(LINES_PER_ABILITY, linesByPetal.getOrDefault(petal, 0L), petal.toString())));
        }

        /** decision petals-render-the-live-fluid */
        @Test
        void petalFillSamplesTheAtlasSpriteAndBlitsNoBakedPetal() {
            GuiGraphicsExtractor graphics = renderFrame(openWheel());
            List<PetalRenderState> fills = submittedArguments(graphics, "submitGuiElementRenderState",
                    PetalRenderState.class).stream().filter(this::isFill).toList();
            List<PetalRenderState.ScreenVertex> corners = fills.stream()
                    .flatMap(fill -> fill.vertices().stream()).toList();

            assertEquals(openWheel().displayedLayout(0.0f).size(), fills.size());
            assertTrue(corners.stream().allMatch(corner -> corner.u() >= SPRITE.u0() && corner.u() <= SPRITE.u1()
                    && corner.v() >= SPRITE.v0() && corner.v() <= SPRITE.v1()), "a corner samples off the sprite");
            assertEquals(List.of(SPRITE.u0(), SPRITE.u1(), SPRITE.v0(), SPRITE.v1()), List.of(
                    corners.stream().map(PetalRenderState.ScreenVertex::u).min(Float::compare).orElseThrow(),
                    corners.stream().map(PetalRenderState.ScreenVertex::u).max(Float::compare).orElseThrow(),
                    corners.stream().map(PetalRenderState.ScreenVertex::v).min(Float::compare).orElseThrow(),
                    corners.stream().map(PetalRenderState.ScreenVertex::v).max(Float::compare).orElseThrow()));
            assertTrue(submittedArguments(graphics, "blit", Identifier.class).stream()
                    .noneMatch(texture -> texture.getPath().startsWith(BAKED_PETAL_PATH)));
            assertAll(corners.stream().map(corner -> (Executable) () -> {
                assertTiled(corner.u(), corner.x(), CENTER_X, SPRITE.u0(), SPRITE.u1());
                assertTiled(corner.v(), corner.y(), CENTER_Y, SPRITE.v0(), SPRITE.v1());
            }));
        }

        /**
         * A corner samples the sprite at the fraction its screen position falls
         * within its tile of the fixed grid; a corner on a grid line may read
         * either edge of the sprite.
         */
        private static void assertTiled(float sampled, float screen, int center, float low, float high) {
            double normalized = (screen - center) / (double) RADIUS;
            double across = (normalized - PetalMesh.TILE_ORIGIN) / PetalMesh.TILE;
            double fraction = across - Math.floor(across);
            double expected = low + fraction * (high - low);
            boolean onGridLine = fraction < TILE_TOLERANCE || fraction > 1 - TILE_TOLERANCE;
            assertTrue(Math.abs(sampled - expected) < SAMPLE_TOLERANCE
                    || onGridLine && (Math.abs(sampled - low) < SAMPLE_TOLERANCE
                    || Math.abs(sampled - high) < SAMPLE_TOLERANCE),
                    "sampled " + sampled + " expected " + expected + " at " + screen);
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void abilityIconCentersOnItsTipWithTheNameAboveAndTheCostBelow() {
            RadialWheel wheel = openWheel();
            GuiGraphicsExtractor graphics = renderFrame(wheel);
            List<DrawnText> texts = renderTexts(wheel);
            int half = RadialWheelRenderer.ABILITY_ICON_SIZE / 2;

            List<PetalRenderState> submitted = submittedArguments(graphics, "submitGuiElementRenderState",
                    PetalRenderState.class);
            TextureSetup badge = sprites.get(RadialWheelRenderer.badgeIcon(AbilityBadge.WORLD));

            assertAll(wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .map(petal -> (Executable) () -> {
                        PetalMask.Point tip = petal.shape().tipCenter();
                        PetalRenderState iconState = spriteOf(submitted, iconOf(petal));
                        float[] iconBox = boxOf(iconState);
                        int iconX = Math.round(iconBox[0]) + half;
                        int iconY = Math.round(iconBox[1]) + half;
                        assertTrue(Math.hypot(iconX - (CENTER_X + tip.x() * RADIUS),
                                iconY - (CENTER_Y + tip.y() * RADIUS)) <= TIP_TOLERANCE,
                                petal + " icon at " + iconX + "," + iconY);
                        assertEquals(0xFFFFFFFF, iconState.color(), petal + " tint");
                        PetalRenderState badgeState = submitted.get(submitted.indexOf(iconState) + 1);
                        assertTrue(badgeState.textureSetup() == badge, petal + " badge follows");
                        float[] badgeBox = boxOf(badgeState);
                        assertEquals(iconX + half, Math.round(badgeBox[0]), petal + " badge's left edge");
                        assertEquals(iconY - half, Math.round(badgeBox[1]), petal + " badge's row");
                        List<DrawnText> words = texts.stream().filter(text -> text.x() == iconX
                                && Math.abs(text.y() - iconY) <= half + 2 * LINE_HEIGHT).toList();
                        assertEquals(LINES_PER_ABILITY, words.size(), petal + " lines");
                        assertTrue(words.getFirst().y() + LINE_HEIGHT <= iconY - half, petal + " name above");
                        assertTrue(words.getLast().y() >= iconY + half, petal + " cost below");
                    }));
        }

        /**
         * A locked petal's fill draws under the disabled tint an unaffordable
         * petal takes; its unlocked siblings stay bright.
         * decision locked-petal-stays-on-the-wheel
         */
        @Test
        void lockedPetalFillDrawsUnderTheDisabledTint() {
            RadialWheel wheel = openWheel();
            GuiGraphicsExtractor graphics = renderFrame(wheel, LOCKED_ABILITY);
            int disabledTint = RadialWheelRenderer.computeOverlayTint(false, true);
            List<RadialWheel.PetalArc> petals = wheel.displayedLayout(0.0f);
            List<PetalRenderState> fills = submittedArguments(graphics, "submitGuiElementRenderState",
                    PetalRenderState.class).stream().filter(this::isFill).toList();

            assertAll(petals.stream().filter(RadialWheel.PetalArc::isAbility).map(petal -> (Executable) () ->
                    assertEquals(petal.ability() == LOCKED_ABILITY, fills.get(petals.indexOf(petal)).color()
                            == disabledTint, petal + " fill tint")));
        }

        /**
         * A locked petal draws no words and no ability icon, only one item per
         * required item inside the petal, with one red slash over each
         * learned item and none over the rest.
         * decision locked-petal-lists-the-unlearned-items
         */
        @Test
        void lockedPetalDrawsOnlyItsRequiredItemsWithTheLearnedSlashed() {
            RadialWheel wheel = openWheel();
            GuiGraphicsExtractor graphics = renderFrame(wheel, LOCKED_ABILITY);
            RadialWheel.PetalArc locked = wheel.layout().stream()
                    .filter(petal -> petal.isAbility() && petal.ability() == LOCKED_ABILITY).findFirst().orElseThrow();
            List<RadialWheelRenderer.ItemRect> items = submittedArguments(graphics,
                    "submitPictureInPictureRenderState", CutItemRenderState.class).stream()
                    .map(state -> new RadialWheelRenderer.ItemRect(state.x0(), state.y0()))
                    .toList();
            List<PetalRenderState.ScreenVertex> slashCorners = submittedArguments(graphics,
                    "submitGuiElementRenderState", PetalRenderState.class).stream()
                    .filter(state -> state.color() == RadialWheelRenderer.SLASH_COLOR)
                    .flatMap(state -> state.vertices().stream())
                    .toList();
            String lockedIcon = "ability_" + locked.type() + "_" + locked.ability() + ".png";

            int[] lockedTip = RadialWheelRenderer.tipCenter(frameOf(wheel, List.of()), locked);
            assertTrue(wordsDrawn(graphics, color -> color != RadialWheelRenderer.OUTLINE_COLOR).stream()
                    .filter(text -> !isInHub(text))
                    .noneMatch(text -> petalUnder(wheel, text).equals(locked) || text.x() == lockedTip[0]),
                    "a word drew on the locked petal");
            assertTrue(mockingDetails(graphics).getInvocations().stream()
                    .noneMatch(call -> call.getMethod().getName().equals("blit")
                            && call.getArgument(1).toString().endsWith(lockedIcon)), "the locked ability's icon drew");
            assertEquals(REQUIRED.size(), items.size());
            assertAll(IntStream.range(0, items.size()).mapToObj(index -> (Executable) () -> {
                RadialWheelRenderer.ItemRect rect = items.get(index);
                long slashed = slashCorners.stream().filter(cell -> onSquare(rect, cell)).count();
                assertTrue(squareInsidePetal(locked, rect), rect + " leaves the petal");
                assertEquals(REQUIRED.get(index).learned(), slashed > 0, rect + " slash");
            }));
            assertTrue(slashCorners.stream().allMatch(cell -> items.stream()
                    .anyMatch(rect -> onSquare(rect, cell))), "a slash drew off its item");
        }

        /**
         * The item column runs along the petal's center line, strides evenly
         * from the hub outward and centers halfway along the petal.
         * decision locked-petal-lists-the-unlearned-items
         */
        @Test
        void itemColumnRunsAlongThePetalsCenterLineCenteredOnItsLength() {
            RadialWheel wheel = openWheel();
            RadialWheelRenderer.Frame frame = frameOf(wheel, List.of());

            assertAll(wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .map(petal -> (Executable) () -> {
                        List<double[]> centers = RadialWheelRenderer.itemColumn(frame, petal, REQUIRED.size())
                                .stream().map(OpenTypeFrame::centerOf).toList();
                        double sinAngle = Math.sin(petal.center());
                        double cosAngle = Math.cos(petal.center());
                        double middle = (petal.shape().inner() + petal.shape().outer()) / 2 * RADIUS;
                        List<Double> along = centers.stream()
                                .map(c -> (c[0] - CENTER_X) * sinAngle - (c[1] - CENTER_Y) * cosAngle).toList();
                        assertAll(centers.stream().map(c -> (Executable) () -> assertTrue(Math.abs(
                                (c[0] - CENTER_X) * cosAngle + (c[1] - CENTER_Y) * sinAngle) <= COLUMN_TOLERANCE,
                                petal + " item off the center line")));
                        assertEquals(RadialWheelRenderer.ITEM_STRIDE, along.get(1) - along.get(0), COLUMN_TOLERANCE);
                        assertEquals(RadialWheelRenderer.ITEM_STRIDE, along.get(2) - along.get(1), COLUMN_TOLERANCE);
                        assertEquals(middle, along.get(1), COLUMN_TOLERANCE, petal + " column center");
                    }));
        }


        private boolean isFill(PetalRenderState state) {
            return state.textureSetup() == atlas;
        }

        private static Identifier iconOf(RadialWheel.PetalArc petal) {
            return Identifier.fromNamespaceAndPath(Goo.MODID,
                    "textures/goo/ability/ability_" + petal.type() + "_" + petal.ability() + ".png");
        }

        /** The one submission drawing a sprite's texture. */
        private PetalRenderState spriteOf(List<PetalRenderState> submitted, Identifier texture) {
            TextureSetup bound = sprites.get(texture);
            List<PetalRenderState> drawing = submitted.stream().filter(state -> state.textureSetup() == bound)
                    .toList();
            assertEquals(1, drawing.size(), texture + " submissions");
            return drawing.getFirst();
        }

        /** A submission's least x and y on screen, the top-left of an uncut sprite. */
        private static float[] boxOf(PetalRenderState state) {
            return new float[]{
                    state.vertices().stream().map(PetalRenderState.ScreenVertex::x).min(Float::compare).orElseThrow(),
                    state.vertices().stream().map(PetalRenderState.ScreenVertex::y).min(Float::compare).orElseThrow()};
        }

        /**
         * An unlocked ability petal's icon reaches the GUI as quads cut to its
         * petal, carrying the icon's own texture, after the petal's edge; a
         * type petal's icon still blits whole.
         * decision icons-slide-in-from-behind-the-tip
         */
        @Test
        void abilityIconSubmitsCutToItsPetalAfterTheEdgeAndTypeIconsBlitWhole() {
            RadialWheel wheel = openWheel();
            GuiGraphicsExtractor graphics = renderFrame(wheel);
            List<PetalRenderState> submitted = submittedArguments(graphics, "submitGuiElementRenderState",
                    PetalRenderState.class);

            assertAll(wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .map(petal -> (Executable) () -> {
                        PetalRenderState icon = spriteOf(submitted, iconOf(petal));
                        int index = submitted.indexOf(icon);
                        PetalRenderState edge = submitted.get(index - 1);
                        assertFalse(edge.isTextured(), petal + " icon follows its edge");
                        assertAll(icon.vertices().stream().map(corner -> (Executable) () -> assertTrue(
                                PetalMeshTest.insideWithSlack(petal.shape(), (corner.x() - CENTER_X) / RADIUS,
                                        (corner.y() - CENTER_Y) / RADIUS), petal + " corner " + corner)));
                        assertTrue(icon.vertices().stream().allMatch(corner -> corner.u() >= 0 && corner.u() <= 1
                                && corner.v() >= 0 && corner.v() <= 1), petal + " samples off its icon");
                    }));
            assertTrue(mockingDetails(graphics).getInvocations().stream()
                    .noneMatch(call -> call.getMethod().getName().equals("blit")
                            && call.getArgument(1).toString().contains("/ability/")), "an ability icon blitted whole");
            assertEquals(wheel.displayedLayout(0.0f).stream().filter(petal -> !petal.isAbility()).count(),
                    mockingDetails(graphics).getInvocations().stream()
                            .filter(call -> call.getMethod().getName().equals("blit")
                                    && call.getArgument(1).toString().contains("/type/")).count());
        }

        private static org.mockito.invocation.Invocation iconBlit(GuiGraphicsExtractor graphics, String icon) {
            return mockingDetails(graphics).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("blit")
                            && call.getArgument(1).toString().endsWith(icon))
                    .findFirst().orElseThrow();
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void typeIconSitsDeadCenterAlongItsPetal() {
            RadialWheel wheel = openWheel();
            GuiGraphicsExtractor graphics = renderFrame(wheel);
            int half = TYPE_ICON_SIZE / 2;

            assertAll(wheel.displayedLayout(0.0f).stream().filter(petal -> !petal.isAbility())
                    .map(petal -> (Executable) () -> {
                        String icon = "type_" + petal.type() + ".png";
                        var iconBlit = mockingDetails(graphics).getInvocations().stream()
                                .filter(call -> call.getMethod().getName().equals("blit")
                                        && call.getArgument(1).toString().endsWith(icon))
                                .findFirst().orElseThrow();
                        double along = (RadialWheel.HUB_FRACTION + petal.length()) / 2 * RADIUS;
                        double expectedX = CENTER_X + Math.sin(petal.center()) * along;
                        double expectedY = CENTER_Y - Math.cos(petal.center()) * along;
                        assertTrue(Math.hypot((int) iconBlit.getArgument(2) + half - expectedX,
                                (int) iconBlit.getArgument(3) + half - expectedY) <= TIP_TOLERANCE,
                                petal + " icon off its length's center");
                    }));
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void abilityWordsDrawAfterEveryPetalAndIcon() {
            List<String> calls = mockingDetails(renderFrame(openWheel())).getInvocations().stream()
                    .map(call -> call.getMethod().getName()).toList();
            int lastShape = Math.max(calls.lastIndexOf("submitGuiElementRenderState"), calls.lastIndexOf("blit"));

            assertTrue(calls.indexOf("text") > lastShape, "a word drew before shape " + lastShape);
        }

        /** decisions petals-render-the-live-fluid, wedges-take-a-solid-edge */
        @Test
        void petalEdgeStripDrawsUntexturedInTheOpaqueEdgeColor() {
            List<PetalRenderState> edges = submittedArguments(renderFrame(openWheel()),
                    "submitGuiElementRenderState", PetalRenderState.class).stream()
                    .filter(state -> !state.isTextured()).toList();

            assertEquals(openWheel().displayedLayout(0.0f).size(), edges.size());
            assertAll(edges.stream().map(edge -> (Executable) () -> {
                assertEquals(EDGE_COLOR, edge.color());
                assertFalse(edge.vertices().isEmpty());
            }));
        }
    }

    /**
     * An ability petal's content slides in along the petal's center line from
     * past its outer radius to the tip's center as the type opens (decision
     * icons-slide-in-from-behind-the-tip).
     */
    @Nested
    class ContentSlide {

        private static final int TYPES = 16;
        private static final int OPEN_TYPE = 5;
        private static final int ABILITIES = 3;
        private static final int CENTER_X = 400;
        private static final int CENTER_Y = 300;
        private static final int RADIUS = 200;
        private static final int REQUIRED_ITEMS = 3;
        /** How far, in pixels, a point may stray from the center line or an expected spot: integer pixel rounding. */
        private static final double ROUNDING = 1.0;

        private final RadialWheel wheel = openWheel();
        private final RadialWheelRenderer.Frame frame = new RadialWheelRenderer.Frame(wheel, List.of(), List.of(),
                Map.of(), CENTER_X, CENTER_Y, RADIUS, null, 0.0f);
        private final RadialWheel.PetalArc fanned = wheel.displayedLayout(0.0f).stream()
                .filter(RadialWheel.PetalArc::isAbility).findFirst().orElseThrow();

        private static RadialWheel openWheel() {
            RadialWheel opened = new RadialWheel(TYPES, type -> ABILITIES);
            double angle = (OPEN_TYPE + 0.5) * opened.typeArc();
            opened.moveCursor(Math.sin(angle) * RADIUS * 0.6, -Math.cos(angle) * RADIUS * 0.6, RADIUS);
            for (int tick = 0; tick < RingEase.DURATION_TICKS; tick++) {
                opened.tick();
            }
            return opened;
        }

        private RadialWheel.PetalArc at(double openness) {
            return new RadialWheel.PetalArc(fanned.type(), fanned.ability(), fanned.start(), fanned.arc(),
                    fanned.length(), fanned.root(), openness);
        }

        /** How far a screen point lies along the petal's center line from the wheel's center. */
        private double along(double x, double y) {
            return (x - CENTER_X) * Math.sin(fanned.center()) - (y - CENTER_Y) * Math.cos(fanned.center());
        }

        /** How far a screen point lies off the petal's center line. */
        private double across(double x, double y) {
            return (x - CENTER_X) * Math.cos(fanned.center()) + (y - CENTER_Y) * Math.sin(fanned.center());
        }

        private boolean insidePetal(double x, double y) {
            return fanned.shape().contains((x - CENTER_X) / RADIUS, (y - CENTER_Y) / RADIUS);
        }

        @Test
        void fullyOpenRestsOnTheTipCenter() {
            assertEquals(1.0, fanned.openness());
            assertEquals(List.of(RadialWheelRenderer.tipCenter(frame, fanned)[0],
                            RadialWheelRenderer.tipCenter(frame, fanned)[1]),
                    Arrays.stream(RadialWheelRenderer.contentCenter(frame, at(1.0))).boxed().toList());
        }

        @Test
        void closedLiesOnTheCenterLineWithTheIconAndBadgeOutsideThePetal() {
            int[] center = RadialWheelRenderer.contentCenter(frame, at(0.0));
            int half = RadialWheelRenderer.ABILITY_ICON_SIZE / 2;
            int size = RadialWheelRenderer.ABILITY_ICON_SIZE;

            assertEquals(0.0, across(center[0], center[1]), ROUNDING);
            assertTrue(along(center[0], center[1]) > fanned.shape().outer() * RADIUS, "short of the outer radius");
            assertAll(IntStream.range(0, 4).mapToObj(corner -> (Executable) () -> {
                int dx = corner % 2 == 0 ? -half : half;
                int dy = corner / 2 == 0 ? -half : half;
                assertFalse(insidePetal(center[0] + dx, center[1] + dy), "icon corner " + corner);
                assertFalse(insidePetal(center[0] + dx + size, center[1] + dy), "badge corner " + corner);
            }));
        }

        @Test
        void halfOpenLiesBetweenOnTheCenterLine() {
            int[] closed = RadialWheelRenderer.contentCenter(frame, at(0.0));
            int[] half = RadialWheelRenderer.contentCenter(frame, at(0.5));
            int[] open = RadialWheelRenderer.contentCenter(frame, at(1.0));

            assertEquals(0.0, across(half[0], half[1]), ROUNDING);
            assertTrue(along(half[0], half[1]) < along(closed[0], closed[1]), "past the closed point");
            assertTrue(along(half[0], half[1]) > along(open[0], open[1]), "short of the resting point");
        }

        @Test
        void itemColumnRestsInPlaceFullyOpenAndRidesOutByTheIconsOffsetClosed() {
            List<RadialWheelRenderer.ItemRect> resting = RadialWheelRenderer.itemColumn(frame, at(1.0),
                    REQUIRED_ITEMS);
            List<RadialWheelRenderer.ItemRect> closed = RadialWheelRenderer.itemColumn(frame, at(0.0),
                    REQUIRED_ITEMS);
            int[] iconClosed = RadialWheelRenderer.contentCenter(frame, at(0.0));
            int[] iconOpen = RadialWheelRenderer.contentCenter(frame, at(1.0));
            double iconOffset = along(iconClosed[0], iconClosed[1]) - along(iconOpen[0], iconOpen[1]);
            double middle = (fanned.shape().inner() + fanned.shape().outer()) / 2 * RADIUS;
            int half = RadialWheelRenderer.ITEM_ICON_SIZE / 2;

            RadialWheelRenderer.ItemRect centerItem = resting.get(1);
            assertEquals(middle, along(centerItem.left() + half, centerItem.top() + half), ROUNDING);
            assertAll(IntStream.range(0, REQUIRED_ITEMS).mapToObj(index -> (Executable) () -> {
                RadialWheelRenderer.ItemRect from = resting.get(index);
                RadialWheelRenderer.ItemRect to = closed.get(index);
                assertEquals(iconOffset, along(to.left(), to.top()) - along(from.left(), from.top()), 2 * ROUNDING,
                        "item " + index + " offset");
                assertEquals(0.0, across(to.left(), to.top()) - across(from.left(), from.top()), 2 * ROUNDING,
                        "item " + index + " off the center line");
            }));
        }
    }

    /** The cursor names the item icon it rests on (decision locked-petal-lists-the-unlearned-items). */
    @Nested
    class ItemHover {

        private static final Identifier GLASS = Identifier.withDefaultNamespace("glass");
        private static final Identifier SAND = Identifier.withDefaultNamespace("sand");
        private static final RadialWheelRenderer.ItemRect GLASS_RECT = new RadialWheelRenderer.ItemRect(200, 100);
        private static final RadialWheelRenderer.ItemRect SAND_RECT = new RadialWheelRenderer.ItemRect(209, 117);

        private static final PetalMask.Petal FACE = new PetalMask.Petal(0.0, Math.PI / 4, RadialWheel.HUB_FRACTION, 1.0);

        private final List<RadialWheelRenderer.ItemIcon> icons = List.of(
                new RadialWheelRenderer.ItemIcon(GLASS, mock(ItemStack.class), GLASS_RECT, true, FACE),
                new RadialWheelRenderer.ItemIcon(SAND, mock(ItemStack.class), SAND_RECT, false, FACE));

        @Test
        void cursorInsideAnIconAnswersThatItem() {
            assertEquals(SAND, RadialWheelRenderer.itemUnder(icons, SAND_RECT.left() + 1, SAND_RECT.top() + 1).item());
            assertEquals(GLASS, RadialWheelRenderer.itemUnder(icons, GLASS_RECT.left(), GLASS_RECT.top()).item());
        }

        @Test
        void cursorOutsideEveryIconAnswersNone() {
            int size = RadialWheelRenderer.ITEM_ICON_SIZE;
            assertAll(
                    () -> assertNull(RadialWheelRenderer.itemUnder(icons, GLASS_RECT.left() - 1, GLASS_RECT.top())),
                    () -> assertNull(RadialWheelRenderer.itemUnder(icons, GLASS_RECT.left() + size,
                            GLASS_RECT.top()), "right of the first icon, above the second"),
                    () -> assertNull(RadialWheelRenderer.itemUnder(icons, SAND_RECT.left(), SAND_RECT.top() + size)),
                    () -> assertNull(RadialWheelRenderer.itemUnder(icons, SAND_RECT.left() + size, SAND_RECT.top())));
        }
    }

    @Nested
    class ExplicitIcon {

        @Test
        void namespacedIconKeepsItsOwnNamespace() {
            assertEquals(Identifier.fromNamespaceAndPath(Goo.MODID, TEXTURE_PATH),
                    RadialWheelRenderer.resolveAbilityIcon(abilityWithIcon(Goo.MODID + ":" + TEXTURE_PATH)));
        }

        @Test
        void bareIconTakesTheGooNamespace() {
            assertEquals(Identifier.fromNamespaceAndPath(Goo.MODID, TEXTURE_PATH),
                    RadialWheelRenderer.resolveAbilityIcon(abilityWithIcon(TEXTURE_PATH)));
        }

        @Test
        void emptyIconFallsBackToTheConventionPath() {
            assertEquals(Identifier.fromNamespaceAndPath(Goo.MODID, TEXTURE_PATH),
                    RadialWheelRenderer.resolveAbilityIcon(abilityWithIcon("")));
        }
    }

    /** A type petal holding a held ability pulses while the type is not expanded (decision wheel-pulses-the-active-effect). */
    @Nested
    class TypePetalPulse {

        private static final Identifier BLAZE_KINDLE = Identifier.fromNamespaceAndPath(Goo.MODID, "blaze_kindle");
        private static final int BLAZE = 0;
        private static final float QUARTER_PERIOD = PetalPulse.PERIOD_TICKS / 4f;

        private RadialWheelRenderer.Frame frame(Set<Identifier> active) {
            List<ResourceKey<GooTypeDefinition>> types = List.of(GooTypes.BLAZE, GooTypes.LEAF);
            List<List<OfferedAbility>> abilities = List.of(
                    List.of(new OfferedAbility(new ClientAbility(BLAZE_KINDLE, "ability.goo.kindle", "", 0, List.of(),
                            List.of(), 0, Delivery.of(DeliveryKind.SELF), AbilityBadge.BREW, List.of()), List.of())),
                    List.of());
            return new RadialWheelRenderer.Frame(new RadialWheel(types.size(), type -> 1), types, abilities,
                    Map.of(GooTypes.BLAZE, 1000, GooTypes.LEAF, 1000), 0, 0, 100, null, 0f, active, QUARTER_PERIOD);
        }

        @Test
        void anUnexpandedTypeHoldingAnActiveAbilityPulses() {
            int pulsing = RadialWheelRenderer.typePetalTint(frame(Set.of(BLAZE_KINDLE)), BLAZE);
            assertEquals(RadialWheelRenderer.HOVER_ALPHA, ARGB.alpha(pulsing));
            assertTrue(ARGB.red(pulsing) > ARGB.red(RadialWheelRenderer.computeOverlayTint(false, false)));
        }

        @Test
        void aTypeHoldingNothingRests() {
            assertEquals(RadialWheelRenderer.computeOverlayTint(false, false),
                    RadialWheelRenderer.typePetalTint(frame(Set.of()), BLAZE));
        }
    }

    /** The tint a textured wedge blits under still tells hovered and disabled from resting (decision wedges-render-fluid-texture). */
    @Nested
    class OverlayTint {

        private final int resting = RadialWheelRenderer.computeOverlayTint(false, false);

        @Test
        void hoveredIsBrighterAndMoreOpaqueThanResting() {
            int hovered = RadialWheelRenderer.computeOverlayTint(true, false);
            assertAll(
                    () -> assertTrue(ARGB.alpha(hovered) > ARGB.alpha(resting)),
                    () -> assertTrue(ARGB.red(hovered) > ARGB.red(resting)),
                    () -> assertTrue(ARGB.green(hovered) > ARGB.green(resting)),
                    () -> assertTrue(ARGB.blue(hovered) > ARGB.blue(resting)));
        }

        @Test
        void disabledIsDarkerAndDimmerThanResting() {
            int disabled = RadialWheelRenderer.computeOverlayTint(false, true);
            assertAll(
                    () -> assertTrue(ARGB.alpha(disabled) < ARGB.alpha(resting)),
                    () -> assertTrue(ARGB.red(disabled) < ARGB.red(resting)),
                    () -> assertTrue(ARGB.green(disabled) < ARGB.green(resting)),
                    () -> assertTrue(ARGB.blue(disabled) < ARGB.blue(resting)));
        }

        @Test
        void disabledOutweighsHovered() {
            assertEquals(RadialWheelRenderer.computeOverlayTint(false, true),
                    RadialWheelRenderer.computeOverlayTint(true, true));
        }
    }
}
