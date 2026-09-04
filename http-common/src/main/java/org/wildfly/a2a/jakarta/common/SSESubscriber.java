/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.a2a.jakarta.common;

import java.io.PrintWriter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.util.sse.SseFormatter;

public class SSESubscriber implements Flow.Subscriber<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSESubscriber.class);

    private static final ScheduledExecutorService HEARTBEAT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-disconnect-detector");
        t.setDaemon(true);
        return t;
    });
    private static final long HEARTBEAT_INTERVAL_MS = 1000;

    // Hook so testing can wait until the async Subscription is subscribed.
    private static volatile Runnable streamingIsSubscribedRunnable;

    private Flow.Subscription subscription;
    private volatile boolean disconnected;
    private ScheduledFuture<?> heartbeatFuture;

    private final AtomicLong eventId = new AtomicLong(0);
    private final CompletableFuture<Void> streamingComplete;
    private final PrintWriter writer;
    private final ServerCallContext context;

    public SSESubscriber(CompletableFuture<Void> streamingComplete, PrintWriter writer, ServerCallContext context) {
        this.streamingComplete = streamingComplete;
        this.writer = writer;
        this.context = context;
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        SSESubscriber.streamingIsSubscribedRunnable = streamingIsSubscribedRunnable;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        LOGGER.info("SSE onSubscribe: streaming started on thread={}", Thread.currentThread().getName());
        this.subscription = subscription;
        // Request all events upfront (mirrors the a2a-java reference SseResponseWriter, see
        // a2a-java #906): a single-item demand window drops back-to-back emissions from the
        // EventConsumer. The EventConsumer's internal buffer (256 items) is the only bound, and
        // EventConsumer.BUFFER_FLUSH_DELAY_MS guarantees the final write is flushed before
        // onComplete fires — so write-level backpressure via request(1) is neither needed nor safe.
        subscription.request(Long.MAX_VALUE);

        heartbeatFuture = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(
                this::heartbeat, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Notify tests that we are subscribed
        Runnable runnable = streamingIsSubscribedRunnable;
        if (runnable != null) {
            runnable.run();
        }
    }

    @Override
    public void onNext(String item) {
        if (disconnected) {
            LOGGER.info("SSE onNext: ignoring event (already disconnected)");
            return;
        }
        try {
            long id = eventId.getAndIncrement();
            String sseEvent = SseFormatter.formatJsonAsSSE(item, id);

            writer.write(sseEvent);
            writer.flush();

            if (writer.checkError()) {
                LOGGER.info("SSE onNext: write failed for event id={} (client disconnect)", id);
                handleClientDisconnect();
                return;
            }

            String preview = item.length() > 200 ? item.substring(0, 200) + "..." : item;
            LOGGER.info("SSE onNext: event id={} sent, preview={}", id, preview);
        } catch (Exception e) {
            LOGGER.error("Error writing SSE event: {}", e.getMessage(), e);
            onError(e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        LOGGER.info("SSE onError: {} on thread={}", throwable.getMessage(), Thread.currentThread().getName());
        handleClientDisconnect();
        streamingComplete.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        LOGGER.info("SSE onComplete: stream finished normally on thread={}", Thread.currentThread().getName());
        cancelHeartbeat();
        streamingComplete.complete(null);
    }

    public void handleClientDisconnect() {
        if (disconnected) {
            return;
        }
        disconnected = true;
        cancelHeartbeat();
        LOGGER.info("SSE handleClientDisconnect: client disconnected, cancelling subscription and EventConsumer on thread={}",
                Thread.currentThread().getName());
        if (subscription != null) {
            subscription.cancel();
        }
        context.invokeEventConsumerCancelCallback();
        streamingComplete.complete(null);
    }

    private void cancelHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
        }
    }

    private void heartbeat() {
        if (disconnected) {
            return;
        }
        // PrintWriter synchronizes internally, so concurrent writes from the
        // EventConsumer thread and this heartbeat thread are safe. An SSE comment
        // (": ...\n\n") is a complete message — it cannot corrupt an adjacent event.
        writer.write(": ping\n\n");
        writer.flush();
        if (writer.checkError()) {
            LOGGER.info("SSE heartbeat detected client disconnect");
            handleClientDisconnect();
        }
    }
}
