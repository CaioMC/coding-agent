package com.example.codingagent.infrastructure.executor;

final class OutputTail {

    private final int maxChars;
    private final StringBuilder buffer = new StringBuilder();
    private long droppedChars;

    OutputTail(int maxChars) {
        this.maxChars = maxChars;
    }

    synchronized void append(String chunk) {
        this.buffer.append(chunk);
        int excess = this.buffer.length() - this.maxChars;
        if (excess > 0) {
            this.buffer.delete(0, excess);
            this.droppedChars += excess;
        }
    }

    @Override
    public synchronized String toString() {
        if (this.droppedChars == 0) {
            return this.buffer.toString();
        }
        return "[... " + this.droppedChars + " caracteres iniciais omitidos ...]\n" + this.buffer;
    }
}
