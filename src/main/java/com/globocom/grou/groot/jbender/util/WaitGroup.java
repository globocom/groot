
package com.globocom.grou.groot.jbender.util;

import java.util.concurrent.atomic.AtomicLong;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.Strand;

/**
 * Simplified phaser supporting up to {@code Long.MAX_VALUE} fibers.
 */
public class WaitGroup {
  private volatile Strand waiter;
  private AtomicLong running;

  public WaitGroup() {
    running = new AtomicLong();
    waiter = null;
  }

  public void add() {
    running.incrementAndGet();
  }

  public void done() {
    long count = running.decrementAndGet();
    if (count == 0 && waiter != null) {
      waiter.unpark();
    }
  }

  public void await() throws SuspendExecution {
    waiter = Strand.currentStrand();
    while (running.get() > 0) {
      Strand.park();
    }
  }
}
