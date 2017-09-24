
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

package com.globocom.grou.groot.jbender.intervals;

import java.util.Random;

/**
 * Poisson distribution request interval generator.
 */
public class ExponentialIntervalGenerator implements IntervalGenerator {
  private final double nanosPerQuery;
  private final Random rand;

  public ExponentialIntervalGenerator(int queriesPerSecond) {
    nanosPerQuery = 1000000000.0 / queriesPerSecond;
    this.rand = new Random();
  }

  @Override
  public long nextInterval(long nanoTimeSinceStart) {
    return (long) (-Math.log(rand.nextDouble()) * this.nanosPerQuery);
  }
}
