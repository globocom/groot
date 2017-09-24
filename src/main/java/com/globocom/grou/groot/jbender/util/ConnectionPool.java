
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

package com.globocom.grou.groot.jbender.util;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

/**
 * An inconvenient ConnectionPool for sockets that are closeable.
 *
 * Use will typically look like this:
 *
 * <code>
 *  ConnectionPool<T> pool = new ConnectionPool(...);
 *  T t = null;
 *  try {
 *    t = pool.acquire();
 *    ...
 *    pool.release(t);
 *  } catch (Exception ex) {
 *    try {
 *      pool.releaseAfterError(t);
 *    } catch (IOException ioex) {
 *      LOG.warn("Failed to close connection", ioex);
 *    }
 *  }
 * </code>
 *
 * @param <T>
 */
public class ConnectionPool<T extends Closeable> {
  @FunctionalInterface
  public interface SuspendableSupplierWithIO<T> {
    T get() throws IOException, SuspendExecution;
  }

  private final SuspendableSupplierWithIO<T> supplier;
  private final Channel<T> pool;

  public ConnectionPool(SuspendableSupplierWithIO<T> supplier, int maxPoolSize) {
    this.supplier = supplier;
    pool = Channels.newChannel(maxPoolSize, Channels.OverflowPolicy.BLOCK, false, false);
  }

  public T acquire() throws IOException, SuspendExecution {
    T t = pool.tryReceive();
    if (t == null) {
      t = supplier.get();
    }
    return t;
  }

  public void release(T t) throws IOException {
    if (t == null) {
      return;
    }

    boolean released = pool.trySend(t);
    if (!released) {
      t.close();
    }
  }

  public void releaseAfterError(T t) throws IOException {
    if (t == null) {
      return;
    }

    t.close();
  }
}
