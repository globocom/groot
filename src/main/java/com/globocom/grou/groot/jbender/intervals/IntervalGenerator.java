
package com.globocom.grou.groot.jbender.intervals;

/**
 * Request interval duration generator interface.
 */
@FunctionalInterface
public interface IntervalGenerator {
  long nextInterval(long nanoTimeSinceStart);
}
