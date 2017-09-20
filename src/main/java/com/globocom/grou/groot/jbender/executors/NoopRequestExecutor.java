
package com.globocom.grou.groot.jbender.executors;

import co.paralleluniverse.fibers.SuspendExecution;

public class NoopRequestExecutor<Q> implements RequestExecutor<Q, Void> {
  @Override
  public Void execute(final long nanoTime, final Q request) throws SuspendExecution, InterruptedException {
    // NOP
    return null;
  }
}
