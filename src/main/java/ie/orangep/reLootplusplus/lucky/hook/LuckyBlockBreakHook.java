package ie.orangep.reLootplusplus.lucky.hook;

/**
 * Lucky Block break handling is implemented directly in
 * {@link ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlock#onBreak}.
 *
 * <p>No separate Fabric event hook is needed because drop evaluation happens
 * inside the block's own {@code onBreak} override, which runs server-side only.
 *
 * <p>This class is kept as a placeholder for future extensions (e.g. adventure mode,
 * explosion-triggered drops, dispenser drops).
 */
public final class LuckyBlockBreakHook {
    private LuckyBlockBreakHook() {}
}
