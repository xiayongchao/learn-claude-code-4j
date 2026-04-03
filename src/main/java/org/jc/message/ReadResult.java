package org.jc.message;

public class ReadResult {
    private final int size;
    private final boolean shutdown;

    private ReadResult(int size, boolean shutdown) {
        this.size = size;
        this.shutdown = shutdown;
    }

    public static ReadResult of(int size, boolean shutdown) {
        return new ReadResult(size, shutdown);
    }

    public static ReadResult of(int size) {
        return new ReadResult(size, false);
    }

    public static ReadResult of() {
        return new ReadResult(0, false);
    }

    public int getSize() {
        return size;
    }

    public boolean isShutdown() {
        return shutdown;
    }
}
