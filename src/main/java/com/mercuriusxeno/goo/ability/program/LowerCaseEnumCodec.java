package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Locale;

/**
 * Builds the codec every step-param enum shares: the JSON writes the
 * constant's lower-case name, and a name no constant carries refuses at
 * load naming the enum's role.
 */
final class LowerCaseEnumCodec {

    private static final String ERR_UNKNOWN = "Unknown %s: %s";

    private LowerCaseEnumCodec() {
    }

    /**
     * Creates the codec for an enum.
     *
     * @param type the enum class
     * @param what the role named in the refusal, such as "entity filter"
     * @param <E>  the enum type
     * @return the codec
     */
    static <E extends Enum<E>> Codec<E> of(Class<E> type, String what) {
        return Codec.STRING.comapFlatMap(key -> byKey(type, what, key), LowerCaseEnumCodec::key);
    }

    /**
     * Returns the lower-case name a constant is written with.
     *
     * @param constant the constant
     * @param <E>      the enum type
     * @return the key
     */
    static <E extends Enum<E>> String key(E constant) {
        return constant.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Finds the constant written with the key.
     *
     * @param type the enum class
     * @param what the role named in the refusal
     * @param key  the key as written
     * @param <E>  the enum type
     * @return the constant, or an error naming the unknown key
     */
    private static <E extends Enum<E>> DataResult<E> byKey(Class<E> type, String what, String key) {
        for (E constant : type.getEnumConstants()) {
            if (key(constant).equals(key)) {
                return DataResult.success(constant);
            }
        }
        return DataResult.error(() -> String.format(ERR_UNKNOWN, what, key));
    }
}
