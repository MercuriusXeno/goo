package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Names line-drawn wind as a stream's look: while the stream is held, the
 * streaming client draws white and pale gray wind lines that rush straight
 * from the glove, then spiral up, down or out and fade, with snowflakes
 * flitting along them where the step asks for them. The server does nothing
 * for it, so Cold names it with snowflakes and a typhoon stream can name it
 * without: {@code wind snowflakes=true}. Where the step names a tailwind, the
 * lines ride along with the player instead, leaving from just behind it and
 * rushing forward past the camera along the look to curl ahead, the wind
 * carrying a jet: {@code wind tailwind={range=6 cone=30}}. Where the step
 * names inward, the lines run the other way, from inside the cone into the
 * glove, as Vacuum draws its wind in: {@code wind inward=true}. The step
 * names the pitch its held wind sounds at, the plain wind's where it names
 * none, so Vacuum's suction sounds low: {@code wind pitch=0.6}. The step
 * names the two colors its lines blend between, the wind's white and pale
 * gray where it names none, and whether void motes stream in along them:
 * {@code wind light_color=#b48ce8 dark_color=#7a52b0 void_motes=true}.
 * cold-streams-wind-lines-and-snowflakes
 * jet-pushes-along-the-look-while-held
 * vacuum-streaks-wind-lines-inward-to-the-glove
 * vacuum-sounds-a-suction-wind-and-rising-slurps
 * vacuum-wind-lines-run-straight-and-far-in-void-colors-with-motes
 *
 * @param snowflakes whether snowflakes flit along the lines
 * @param tailwind   the tailwind the lines carry the player on, or empty to blow them from the glove
 * @param inward     whether the lines run from inside the cone into the glove rather than out of it
 * @param pitch      the pitch the held wind sounds at
 * @param colors     the two colors the lines blend between
 * @param voidMotes  whether void motes stream along the lines toward the glove
 */
public record WindStep(boolean snowflakes, Optional<Tailwind> tailwind, boolean inward, float pitch,
                       LineColors colors, boolean voidMotes) implements TakesNoEntity {

    /** The held wind's pitch where the step names none. */
    public static final float PLAIN_PITCH = 1.0f;

    private static final String NAME = "wind";
    private static final String FIELD_SNOWFLAKES = "snowflakes";
    private static final String FIELD_TAILWIND = "tailwind";
    private static final String FIELD_INWARD = "inward";
    private static final String FIELD_PITCH = "pitch";
    private static final String FIELD_LIGHT_COLOR = "light_color";
    private static final String FIELD_DARK_COLOR = "dark_color";
    private static final String FIELD_VOID_MOTES = "void_motes";
    private static final String HEX_MARK = "#";
    private static final int HEX_RADIX = 16;
    private static final int RGB = 0xFFFFFF;
    private static final String HEX_FORMAT = "#%06x";
    private static final String NOT_A_COLOR = "Not an #rrggbb color: ";

    /** A color written as #rrggbb. */
    private static final Codec<Integer> HEX_COLOR = Codec.STRING.comapFlatMap(WindStep::parseColor,
            rgb -> String.format(HEX_FORMAT, rgb & RGB));

    /**
     * The two colors a line blends between, each line leaning its own share from the light to the dark.
     * vacuum-wind-lines-run-straight-and-far-in-void-colors-with-motes
     *
     * @param light the color a line leaning nothing draws, RGB
     * @param dark  the color a line leaning wholly draws, RGB
     */
    public record LineColors(int light, int dark) {

        /** The wind's white and pale gray. */
        public static final LineColors WIND = new LineColors(0xF4F8FF, 0xD6DDE8);
    }

    /**
     * A jet's tailwind: the cone the lines rush forward through, riding along with the player.
     *
     * @param range       how far ahead the lines rush, in blocks
     * @param coneDegrees the cone's apex angle, in degrees
     */
    public record Tailwind(double range, double coneDegrees) {

        private static final String FIELD_RANGE = "range";
        private static final String FIELD_CONE = "cone";

        /** Codec for the tailwind. */
        public static final Codec<Tailwind> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.DOUBLE.fieldOf(FIELD_RANGE).forGetter(Tailwind::range),
                Codec.DOUBLE.fieldOf(FIELD_CONE).forGetter(Tailwind::coneDegrees)
        ).apply(inst, Tailwind::new));
    }

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<WindStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.BOOL.optionalFieldOf(FIELD_SNOWFLAKES, false).forGetter(WindStep::snowflakes),
            Tailwind.CODEC.optionalFieldOf(FIELD_TAILWIND).forGetter(WindStep::tailwind),
            Codec.BOOL.optionalFieldOf(FIELD_INWARD, false).forGetter(WindStep::inward),
            Codec.floatRange(0, Float.MAX_VALUE).optionalFieldOf(FIELD_PITCH, PLAIN_PITCH).forGetter(WindStep::pitch),
            HEX_COLOR.optionalFieldOf(FIELD_LIGHT_COLOR, LineColors.WIND.light()).forGetter(step -> step.colors().light()),
            HEX_COLOR.optionalFieldOf(FIELD_DARK_COLOR, LineColors.WIND.dark()).forGetter(step -> step.colors().dark()),
            Codec.BOOL.optionalFieldOf(FIELD_VOID_MOTES, false).forGetter(WindStep::voidMotes)
    ).apply(inst, (snowflakes, tailwind, inward, pitch, light, dark, voidMotes) ->
            new WindStep(snowflakes, tailwind, inward, pitch, new LineColors(light, dark), voidMotes)));

    /**
     * The registered type.
     */
    public static final StepType<WindStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * Wind blown from the glove.
     *
     * @param snowflakes whether snowflakes flit along the lines
     */
    public WindStep(boolean snowflakes) {
        this(snowflakes, Optional.empty());
    }

    /**
     * Wind blown out from the glove, or carried as a tailwind.
     *
     * @param snowflakes whether snowflakes flit along the lines
     * @param tailwind   the tailwind the lines carry the player on, or empty to blow them from the glove
     */
    public WindStep(boolean snowflakes, Optional<Tailwind> tailwind) {
        this(snowflakes, tailwind, false, PLAIN_PITCH, LineColors.WIND, false);
    }

    private static DataResult<Integer> parseColor(String written) {
        if (!written.startsWith(HEX_MARK)) {
            return DataResult.error(() -> NOT_A_COLOR + written);
        }
        try {
            return DataResult.success(Integer.parseInt(written.substring(HEX_MARK.length()), HEX_RADIX) & RGB);
        } catch (NumberFormatException e) {
            return DataResult.error(() -> NOT_A_COLOR + written);
        }
    }

    @Override
    public StepType<WindStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of();
    }
}
