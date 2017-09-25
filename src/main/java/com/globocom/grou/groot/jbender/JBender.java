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

package com.globocom.grou.groot.jbender;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.FiberScheduler;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.Strand;
import co.paralleluniverse.strands.StrandFactory;
import co.paralleluniverse.strands.SuspendableCallable;
import co.paralleluniverse.strands.channels.ReceivePort;
import co.paralleluniverse.strands.channels.SendPort;
import co.paralleluniverse.strands.concurrent.Semaphore;
import com.globocom.grou.groot.jbender.events.TimingEvent;
import com.globocom.grou.groot.jbender.executors.RequestExecutor;
import com.globocom.grou.groot.jbender.intervals.IntervalGenerator;
import com.globocom.grou.groot.jbender.util.AHC2ParameterizedRequest;
import com.globocom.grou.groot.jbender.util.WaitGroup;
import com.globocom.grou.groot.statsd.StatsdService;
import io.galeb.statsd.StatsDClient;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public final class JBender {

  private static final Logger LOG = LoggerFactory.getLogger(JBender.class);

  private final StatsDClient statsdClient;

  @Autowired
  public JBender(StatsdService statsdService) {
    this.statsdClient = statsdService.client();
  }

  /**
   * Run a load test with the given throughput, using as many fibers as necessary.
   *
   * This method can be run in any strand; thread-fiber synchronization is more expensive than
   * fiber-fiber synchronization though, so if requests are being performed by fibers its best
   * to call this method inside a fiber.
   *
   * @param intervalGen provides the interval between subsequent requests (in nanoseconds). This
   *                    controls the throughput of the load test.
   * @param warmupRequests the number of requests to use as "warmup" for the load tester and the
   *                       service. These requests will not have TimingEvents generated in the
   *                       eventChannel, but will be sent to the remote service at the requested
   *                       rate.
   * @param requests provides requests for the load test, must be closed by the caller to stop the
   *                 load test (the load test will continue for as long as this channel is open,
   *                 even if there are no requests arriving).
   * @param executor executes the requests provided by the requests channel, returning a response
   *                 object.
   * @param eventChannel a TimingEvent is sent on this channel for every request executed during
   *                     the load test (whether the request succeeds or not).
   * @throws SuspendExecution
   * @throws InterruptedException
   */
  public void loadTestThroughput(final IntervalGenerator intervalGen,
                                                   final int warmupRequests,
                                                   final ReceivePort<AHC2ParameterizedRequest> requests,
                                                   final RequestExecutor<AHC2ParameterizedRequest, Response> executor,
                                                   final SendPort<TimingEvent<Response>> eventChannel)
    throws InterruptedException, SuspendExecution
  {
    loadTestThroughput(intervalGen, warmupRequests, requests, executor, eventChannel, null, null);
  }

  /**
   * Run a load test with a given throughput, using as many fibers as necessary.
   *
   * This method can be run in any strand; thread-fiber synchronization is more expensive than
   * fiber-fiber synchronization though, so if requests are being performed by fibers its best
   * to call this method inside a fiber.
   *
   * @param intervalGen provides the interval between subsequent requests (in nanoseconds). This
   *                    controls the throughput of the load test.
   * @param warmupRequests the number of requests to use as "warmup" for the load tester and the
   *                       service. These requests will not have TimingEvents generated in the
   *                       eventChannel, but will be sent to the remote service at the requested
   *                       rate.
   * @param requests provides requests for the load test, must be closed by the caller to stop the
   *                 load test (the load test will continue for as long as this channel is open,
   *                 even if there are no requests arriving).
   * @param executor executes the requests provided by the requests channel, returning a response
   *                 object.
   * @param eventChannel a TimingEvent is sent on this channel for every request executed during
   *                     the load test (whether the request succeeds or not).
   * @param fiberScheduler an optional scheduler for fibers that will perform the requests (the
   *                       default one will be used if {@code null}).
   * @throws SuspendExecution
   * @throws InterruptedException
   */
  public void loadTestThroughput(final IntervalGenerator intervalGen,
                                                   final int warmupRequests,
                                                   final ReceivePort<AHC2ParameterizedRequest> requests,
                                                   final RequestExecutor<AHC2ParameterizedRequest, Response> executor,
                                                   final SendPort<TimingEvent<Response>> eventChannel,
                                                   final FiberScheduler fiberScheduler)
          throws InterruptedException, SuspendExecution
  {
    loadTestThroughput(intervalGen, warmupRequests, requests, executor, eventChannel, fiberScheduler, null);
  }

  /**
   * Run a load test with a given throughput, using as many fibers as necessary.
   *
   * This method can be run in any strand; thread-fiber synchronization is more expensive than
   * fiber-fiber synchronization though, so if requests are being performed by fibers its best
   * to call this method inside a fiber.
   *
   * @param intervalGen provides the interval between subsequent requests (in nanoseconds). This
   *                    controls the throughput of the load test.
   * @param warmupRequests the number of requests to use as "warmup" for the load tester and the
   *                       service. These requests will not have TimingEvents generated in the
   *                       eventChannel, but will be sent to the remote service at the requested
   *                       rate.
   * @param requests provides requests for the load test, must be closed by the caller to stop the
   *                 load test (the load test will continue for as long as this channel is open,
   *                 even if there are no requests arriving).
   * @param executor executes the requests provided by the requests channel, returning a response
   *                 object.
   * @param eventChannel a TimingEvent is sent on this channel for every request executed during
   *                     the load test (whether the request succeeds or not).
   * @param strandFactory an optional factory for strands that will perform the requests (the
   *                      default one will be used if {@code null}).
   * @throws SuspendExecution
   * @throws InterruptedException
   */
  public void loadTestThroughput(final IntervalGenerator intervalGen,
                                                   final int warmupRequests,
                                                   final ReceivePort<AHC2ParameterizedRequest> requests,
                                                   final RequestExecutor<AHC2ParameterizedRequest, Response> executor,
                                                   final SendPort<TimingEvent<Response>> eventChannel,
                                                   final StrandFactory strandFactory)
          throws InterruptedException, SuspendExecution
  {
    loadTestThroughput(intervalGen, warmupRequests, requests, executor, eventChannel, null, strandFactory);
  }

  /**
   * Run a load test with a given number of fibers, making as many requests as possible.
   *
   * This method can be run in any strand; thread-fiber synchronization is more expensive than
   * fiber-fiber synchronization though, so if requests are being performed by fibers its best
   * to call this method inside a fiber.
   *
   * @param concurrency the number of Fibers to run. Each Fiber will execute requests serially with
   *                    as little overhead as possible.
   * @param warmupRequests the number of requests to use when warming up the load tester and the
   *                       remote service. These requests will not not have TimingEvents generated
   *                       in the eventChannel, but will be sent to the remote service.
   * @param requests provides requests for the load test and must be closed by the caller to stop
   *                 the load test (the load test will continue for as long as this channel is
   *                 open, even if there are no requests arriving).
   * @param executor executes the requets provided by the requests channel, returning a response
   *                 object.
   * @param eventChannel a TimingEvent is sent on this channel for every request executed during
   *                     the load test (whether the request succeeds or not).
   * @throws SuspendExecution
   * @throws InterruptedException
   */
  public void loadTestConcurrency(final int concurrency,
                                                    final int warmupRequests,
                                                    final ReceivePort<AHC2ParameterizedRequest> requests,
                                                    final RequestExecutor<AHC2ParameterizedRequest, Response> executor,
                                                    final SendPort<TimingEvent<Response>> eventChannel)
    throws SuspendExecution, InterruptedException
  {
    loadTestConcurrency(concurrency, warmupRequests, requests, executor, eventChannel, null, null);
  }

  /**
   * Run a load test with a given number of fibers, making as many requests as possible.
   *
   * This method can be run in any strand; thread-fiber synchronization is more expensive than
   * fiber-fiber synchronization though, so if requests are being performed by fibers its best
   * to call this method inside a fiber.
   *
   * @param concurrency the number of Fibers to run. Each Fiber will execute requests serially with
   *                    as little overhead as possible.
   * @param warmupRequests the number of requests to use when warming up the load tester and the
   *                       remote service. These requests will not not have TimingEvents generated
   *                       in the eventChannel, but will be sent to the remote service.
   * @param requests provides requests for the load test and must be closed by the caller to stop
   *                 the load test (the load test will continue for as long as this channel is
   *                 open, even if there are no requests arriving).
   * @param executor executes the requets provided by the requests channel, returning a response
   *                 object.
   * @param eventChannel a TimingEvent is sent on this channel for every request executed during
   *                     the load test (whether the request succeeds or not).
   * @param fiberScheduler an optional scheduler for fibers that will perform the requests (the
   *                       default one will be used if {@code null}).
   * @throws SuspendExecution
   * @throws InterruptedException
   */
  public void loadTestConcurrency(final int concurrency,
                                                    final int warmupRequests,
                                                    final ReceivePort<AHC2ParameterizedRequest> requests,
                                                    final RequestExecutor<AHC2ParameterizedRequest, Response> executor,
                                                    final SendPort<TimingEvent<Response>> eventChannel,
                                                    final FiberScheduler fiberScheduler)
          throws SuspendExecution, InterruptedException
  {
    loadTestConcurrency(concurrency, warmupRequests, requests, executor, eventChannel, fiberScheduler, null);
  }

  /**
   * Run a load test with a given number of fibers, making as many requests as possible.
   *
   * This method can be run in any strand; thread-fiber synchronization is more expensive than
   * fiber-fiber synchronization though, so if requests are being performed by fibers its best
   * to call this method inside a fiber.
   *
   * @param concurrency the number of Fibers to run. Each Fiber will execute requests serially with
   *                    as little overhead as possible.
   * @param warmupRequests the number of requests to use when warming up the load tester and the
   *                       remote service. These requests will not not have TimingEvents generated
   *                       in the eventChannel, but will be sent to the remote service.
   * @param requests provides requests for the load test and must be closed by the caller to stop
   *                 the load test (the load test will continue for as long as this channel is
   *                 open, even if there are no requests arriving).
   * @param executor executes the requets provided by the requests channel, returning a response
   *                 object.
   * @param eventChannel a TimingEvent is sent on this channel for every request executed during
   *                     the load test (whether the request succeeds or not).
   * @param strandFactory an optional factory for strands that will perform the requests (the
   *                      default one will be used if {@code null}).
   * @throws SuspendExecution
   * @throws InterruptedException
   */
  public void loadTestConcurrency(final int concurrency,
                                                    final int warmupRequests,
                                                    final ReceivePort<AHC2ParameterizedRequest> requests,
                                                    final RequestExecutor<AHC2ParameterizedRequest, Response> executor,
                                                    final SendPort<TimingEvent<Response>> eventChannel,
                                                    final StrandFactory strandFactory)
          throws SuspendExecution, InterruptedException
  {
    loadTestConcurrency(concurrency, warmupRequests, requests, executor, eventChannel, null, strandFactory);
  }

  private static class RequestExecOutcome<Response> {
    final long execTime;
    final Response response;
    final Exception exception;

    public RequestExecOutcome(final long execTime, final Response response, final Exception exception) {
      this.execTime = execTime;
      this.response = response;
      this.exception = exception;
    }
  }

  private void loadTestThroughput(final IntervalGenerator intervalGen,
                                                    int warmupRequests,
                                                    final ReceivePort<AHC2ParameterizedRequest> requests,
                                                    final RequestExecutor<AHC2ParameterizedRequest, Response> executor,
                                                    final SendPort<TimingEvent<Response>> eventChannel,
                                                    final FiberScheduler fiberScheduler,
                                                    final StrandFactory strandFactory)
          throws SuspendExecution, InterruptedException
  {
    final long startNanos = System.nanoTime();

    try {
      long overageNanos = 0;
      long overageStart = System.nanoTime();

      final WaitGroup waitGroup = new WaitGroup();
      while (true) {
        final long receiveNanosStart = System.nanoTime();
        final AHC2ParameterizedRequest request = requests.receive();
        LOG.trace("Receive request time: {}", System.nanoTime() - receiveNanosStart);
        if (request == null) {
          break;
        }

        // Wait before dispatching request as much as generated, minus the remaining dispatching overhead
        // to be compensated for (up to having 0 waiting time of course, not negative)
        long waitNanos = intervalGen.nextInterval(System.nanoTime() - startNanos);
        final long adjust = Math.min(waitNanos, overageNanos);
        waitNanos -= adjust;
        overageNanos -= adjust;

        // Sleep in the accepting fiber
        long sleepNanosStart = System.nanoTime();
        Strand.sleep(waitNanos, TimeUnit.NANOSECONDS);
        LOG.trace("Sleep time: {}", System.nanoTime() - sleepNanosStart);

        // Increment wait group count for new request handler
        waitGroup.add();
        final long curWaitNanos = waitNanos;
        final long curWarmupRequests = warmupRequests;
        final long curOverageNanos = overageNanos;

        final SuspendableCallable<Void> sc = () -> {
          try {
            final RequestExecOutcome<Response> outcome = executeRequest(request, executor);
            if (curWarmupRequests <= 0) {
              report(curWaitNanos, curOverageNanos, outcome, eventChannel);
            }
          } finally {
            // Complete, decrementing wait group count
            waitGroup.done();
          }
          return null;
        };
        startFiber(fiberScheduler, strandFactory, sc);

        final long nowNanos = System.nanoTime();
        overageNanos += nowNanos - overageStart - waitNanos;
        overageStart = nowNanos;
        warmupRequests = Math.max(warmupRequests - 1, 0);
      }

      // Wait for all outstanding requests
      waitGroup.await();
    } finally {
      eventChannel.close();
    }
  }

  private void loadTestConcurrency(final int concurrency,
                                                     int warmupRequests,
                                                     final ReceivePort<AHC2ParameterizedRequest> requests,
                                                     final RequestExecutor<AHC2ParameterizedRequest, Response> executor,
                                                     final SendPort<TimingEvent<Response>> eventChannel,
                                                     final FiberScheduler fiberScheduler,
                                                     final StrandFactory strandFactory)
          throws SuspendExecution, InterruptedException
  {
    try {
      final WaitGroup waitGroup = new WaitGroup();
      final Semaphore running = new Semaphore(concurrency);

      while (true) {
        final AHC2ParameterizedRequest request = requests.receive();
        if (request == null) {
          break;
        }

        running.acquire();
        waitGroup.add();
        final long curWarmupRequests = warmupRequests;
        final SuspendableCallable<Void> sc = () -> {
          try {
            final RequestExecOutcome<Response> outcome = executeRequest(request, executor);
            if (curWarmupRequests <= 0) {
              report(0, 0, outcome, eventChannel);
            }
          } finally {
            running.release();
            waitGroup.done();
          }
          return null;
        };
        startFiber(fiberScheduler, strandFactory, sc);

        warmupRequests = Math.max(warmupRequests - 1, 0);
      }

      waitGroup.await();
    } finally {
      eventChannel.close();
    }
  }

  private void startFiber(FiberScheduler fiberScheduler, StrandFactory strandFactory, SuspendableCallable<Void> sc) {
    if (fiberScheduler != null) {
      new Fiber<>(fiberScheduler, sc).start();
    } else if (strandFactory != null) {
      strandFactory.newStrand(sc).start();
    } else {
      new Fiber<>(sc).start();
    }
  }

  private RequestExecOutcome<Response> executeRequest(final AHC2ParameterizedRequest request, final RequestExecutor<AHC2ParameterizedRequest, Response> executor)
          throws SuspendExecution, InterruptedException
  {
    Response response = null;
    Exception exc = null;
    final long startNanos = System.nanoTime();
    try {
      response = executor.statsdClient(statsdClient).execute(startNanos, request);
    } catch (final Exception ex) {
      if (LOG.isDebugEnabled()) LOG.debug("Exception while executing request {}", request, ex);
      exc = ex;
    }
    return new RequestExecOutcome<>(System.nanoTime() - startNanos, response, exc);
  }

  private void report(final long curWaitNanos,
                                   long curOverageNanos,
                                   final RequestExecOutcome<Response> outcome,
                                   final SendPort<TimingEvent<Response>> eventChannel)
      throws SuspendExecution, InterruptedException
  {
    if (outcome.exception == null) {
      eventChannel.send(
          new TimingEvent<>(curWaitNanos, outcome.execTime, curOverageNanos, outcome.response));
    } else {
      eventChannel.send(
          new TimingEvent<>(curWaitNanos, outcome.execTime, curOverageNanos, outcome.exception));
    }
  }
}
