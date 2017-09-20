
package com.globocom.grou.groot.jbender.intervals;

public class ConstantIntervalGenerator implements IntervalGenerator {
  private final long interval;

  public ConstantIntervalGenerator(long interval) {
    this.interval = interval;
  }

  @Override
  public long nextInterval(long nanoTimeSinceStart) {
    return interval;
  }
}
