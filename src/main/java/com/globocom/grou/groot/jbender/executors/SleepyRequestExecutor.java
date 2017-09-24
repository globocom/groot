
/*
 * Copyright (c) 2017-2017 Globo.com
 * All rights reserved.
 *
 * This source is subject to the Apache License, Version 2.0.
 * Please see the LICENSE file for more information.
 *
 * Authors: See AUTHORS file
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globocom.grou.groot.jbender.executors;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.Strand;

public class SleepyRequestExecutor<Q> implements RequestExecutor<Q, Void> {
  private final int sleepMillis;
  private final int sleepNanos;

  public SleepyRequestExecutor(int sleepMillis, int sleepNanos) {
    this.sleepMillis = sleepMillis;
    this.sleepNanos = sleepNanos;
  }

  @Override
  public Void execute(final long nanoTime, final Q request) throws SuspendExecution, InterruptedException {
    Strand.sleep(sleepMillis, sleepNanos);
    return null;
  }
}
