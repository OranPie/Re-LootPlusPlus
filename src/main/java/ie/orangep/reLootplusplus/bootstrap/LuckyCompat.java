package ie.orangep.reLootplusplus.bootstrap;

import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * Static holder for the {@link LuckyBootstrap} implementation discovered at startup.
 * Also holds the client-side screen factory (set by LuckyCompatClient on the client).
 */
public final class LuckyCompat {

    private static LuckyBootstrap impl = LuckyBootstrap.NOOP;
    @SuppressWarnings("rawtypes")
    private static BiFunction screenFactory = null;

    private LuckyCompat() {}

    public static void set(LuckyBootstrap impl) {
        LuckyCompat.impl = impl;
    }

    public static LuckyBootstrap get() {
        return impl;
    }

    @SuppressWarnings("unchecked")
    public static void setDropLinesScreenFactory(BiFunction<Object, Object, Object> factory) {
        LuckyCompat.screenFactory = factory;
    }

    /**
     * Creates the DropLines screen for the given pack id.
     * Returns null if the screen factory has not been registered (e.g., on dedicated server).
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static Object createDropLinesScreen(Object parent, Object packId) {
        if (screenFactory == null) return null;
        return screenFactory.apply(parent, packId);
    }
}
