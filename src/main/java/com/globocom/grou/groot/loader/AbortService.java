/*
 * Copyright (c) 2017-2018 Globo.com
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

package com.globocom.grou.groot.loader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AbortService {

    private static final Log LOGGER = LogFactory.getLog(AbortService.class);

    private AtomicBoolean abort;
    private TestExecutor executor;
    private AtomicBoolean started = new AtomicBoolean(false);

    public synchronized void start(final AtomicBoolean abort, final TestExecutor executor) {
        this.abort = abort;
        this.executor = executor;
        started.set(true);
    }

    public synchronized void stop() {
        try {
            started.set(false);
            abort.set(false);
            executor.interrupt();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 5000L)
    public void check() {
        if (started.get() && abort.get()) {
            stop();
        }
    }
}
