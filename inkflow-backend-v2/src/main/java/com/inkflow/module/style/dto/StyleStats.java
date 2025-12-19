package com.inkflow.module.style.dto;

/**
 * 风格学习统计
 */
public record StyleStats(
    long sampleCount,
    double averageEditRatio,
    long totalWordCount
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long sampleCount;
        private double averageEditRatio;
        private long totalWordCount;

        public Builder sampleCount(long sampleCount) {
            this.sampleCount = sampleCount;
            return this;
        }

        public Builder averageEditRatio(double averageEditRatio) {
            this.averageEditRatio = averageEditRatio;
            return this;
        }

        public Builder totalWordCount(long totalWordCount) {
            this.totalWordCount = totalWordCount;
            return this;
        }

        public StyleStats build() {
            return new StyleStats(sampleCount, averageEditRatio, totalWordCount);
        }
    }
}
