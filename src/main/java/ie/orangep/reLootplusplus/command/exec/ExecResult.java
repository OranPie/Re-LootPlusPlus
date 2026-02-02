package ie.orangep.reLootplusplus.command.exec;

public final class ExecResult {
    private final int successCount;

    public ExecResult(int successCount) {
        this.successCount = successCount;
    }

    public int successCount() {
        return successCount;
    }

    public static ExecResult success(int count) {
        return new ExecResult(count);
    }
}
