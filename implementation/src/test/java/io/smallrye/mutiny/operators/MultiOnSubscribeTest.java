package io.smallrye.mutiny.operators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.operators.multi.MultiOnSubscribeCall;
import io.smallrye.mutiny.operators.multi.MultiOnSubscribeInvokeOp;
import io.smallrye.mutiny.subscription.UniEmitter;
import io.smallrye.mutiny.test.AssertSubscriber;

public class MultiOnSubscribeTest {

    @Test
    public void testInvoke() {
        AtomicInteger count = new AtomicInteger();
        AtomicReference<Subscription> reference = new AtomicReference<>();
        Multi<Integer> multi = Multi.createFrom().items(1, 2, 3)
                .onSubscribe().invoke(s -> {
                    reference.set(s);
                    count.incrementAndGet();
                });

        AssertSubscriber<Integer> subscriber = AssertSubscriber.create(10);

        assertThat(count).hasValue(0);
        assertThat(reference).hasValue(null);

        multi.subscribe().withSubscriber(subscriber).assertCompletedSuccessfully().assertReceived(1, 2, 3);

        assertThat(count).hasValue(1);
        assertThat(reference).doesNotHaveValue(null);

        AssertSubscriber<Integer> subscriber2 = AssertSubscriber.create(10);
        multi.subscribe().withSubscriber(subscriber2).assertCompletedSuccessfully().assertReceived(1, 2, 3);

        assertThat(count).hasValue(2);
        assertThat(reference).doesNotHaveValue(null);
    }

    @Test
    public void testDeprecatedOnSubscribed() {
        AtomicInteger count = new AtomicInteger();
        AtomicReference<Subscription> reference = new AtomicReference<>();
        Multi<Integer> multi = Multi.createFrom().items(1, 2, 3)
                .onSubscribe().invoke(s -> {
                    reference.set(s);
                    count.incrementAndGet();
                });

        AssertSubscriber<Integer> subscriber = AssertSubscriber.create(10);

        assertThat(count).hasValue(0);
        assertThat(reference).hasValue(null);

        multi.subscribe().withSubscriber(subscriber).assertCompletedSuccessfully().assertReceived(1, 2, 3);

        assertThat(count).hasValue(1);
        assertThat(reference).doesNotHaveValue(null);

        AssertSubscriber<Integer> subscriber2 = AssertSubscriber.create(10);
        multi.subscribe().withSubscriber(subscriber2).assertCompletedSuccessfully().assertReceived(1, 2, 3);

        assertThat(count).hasValue(2);
        assertThat(reference).doesNotHaveValue(null);
    }

    @Test
    public void testCall() {
        AtomicInteger count = new AtomicInteger();
        AtomicReference<Subscription> reference = new AtomicReference<>();
        AtomicReference<Subscription> sub = new AtomicReference<>();
        Multi<Integer> multi = Multi.createFrom().items(1, 2, 3)
                .onSubscribe().call(s -> {
                    reference.set(s);
                    count.incrementAndGet();
                    return Uni.createFrom().nullItem()
                            .onSubscribe().invoke(sub::set);
                });

        AssertSubscriber<Integer> subscriber = AssertSubscriber.create(10);

        assertThat(count).hasValue(0);
        assertThat(reference).hasValue(null);

        multi.subscribe().withSubscriber(subscriber).assertCompletedSuccessfully().assertReceived(1, 2, 3);

        assertThat(count).hasValue(1);
        assertThat(reference).doesNotHaveValue(null);

        AssertSubscriber<Integer> subscriber2 = AssertSubscriber.create(10);
        multi.subscribe().withSubscriber(subscriber2).assertCompletedSuccessfully().assertReceived(1, 2, 3);

        assertThat(count).hasValue(2);
        assertThat(reference).doesNotHaveValue(null);
    }

    @Test
    public void testInvokeThrowingException() {
        Multi<Integer> multi = Multi.createFrom().items(1, 2, 3)
                .onSubscribe().invoke(s -> {
                    throw new IllegalStateException("boom");
                });

        AssertSubscriber<Integer> subscriber = AssertSubscriber.create();

        multi.subscribe().withSubscriber(subscriber)
                .assertHasFailedWith(IllegalStateException.class, "boom");

    }

    @Test
    public void testCallThrowingException() {
        Multi<Integer> multi = Multi.createFrom().items(1, 2, 3)
                .onSubscribe().call(s -> {
                    throw new IllegalStateException("boom");
                });

        AssertSubscriber<Integer> subscriber = AssertSubscriber.create();

        multi.subscribe().withSubscriber(subscriber)
                .assertHasFailedWith(IllegalStateException.class, "boom");

    }

    @Test
    public void testCallProvidingFailure() {
        Multi<Integer> multi = Multi.createFrom().items(1, 2, 3)
                .onSubscribe().call(s -> Uni.createFrom().failure(new IOException("boom")));

        AssertSubscriber<Integer> subscriber = AssertSubscriber.create();

        multi.subscribe().withSubscriber(subscriber)
                .assertHasFailedWith(IOException.class, "boom");

    }

    @Test
    public void testCallReturningNullUni() {
        Multi<Integer> multi = Multi.createFrom().items(1, 2, 3)
                .onSubscribe().call(s -> null);

        AssertSubscriber<Integer> subscriber = AssertSubscriber.create();

        multi.subscribe().withSubscriber(subscriber)
                .assertHasFailedWith(NullPointerException.class, "`null`");
    }

    @Test
    public void testThatInvokeConsumerCannotBeNull() {
        assertThrows(IllegalArgumentException.class, () -> Multi.createFrom().items(1, 2, 3)
                .onSubscribe().invoke((Consumer<? super Subscription>) null));
    }

    @Test
    public void testThatCallFunctionCannotBeNull() {
        assertThrows(IllegalArgumentException.class, () -> Multi.createFrom().items(1, 2, 3)
                .onSubscribe().call((Function<? super Subscription, Uni<?>>) null));
    }

    @Test
    public void testThatInvokeUpstreamCannotBeNull() {
        assertThrows(IllegalArgumentException.class, () -> new MultiOnSubscribeInvokeOp<>(null, s -> {
        }));
    }

    @Test
    public void testThatCallUpstreamCannotBeNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new MultiOnSubscribeCall<>(null, s -> Uni.createFrom().nullItem()));
    }

    @Test
    public void testThatSubscriptionIsNotPassedDownstreamUntilInvokeCallbackCompletes() {
        CountDownLatch latch = new CountDownLatch(1);
        AssertSubscriber<Integer> subscriber = Multi.createFrom().items(1, 2, 3)
                .onSubscribe().invoke(s -> {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                })
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().withSubscriber(AssertSubscriber.create(3));

        subscriber.assertNotSubscribed();
        latch.countDown();
        subscriber.await()
                .assertSubscribed()
                .assertCompletedSuccessfully().assertReceived(1, 2, 3);
    }

    @Test
    public void testThatSubscriptionIsNotPassedDownstreamUntilProducedUniCompletes() {
        AtomicReference<UniEmitter<? super Integer>> emitter = new AtomicReference<>();
        AssertSubscriber<Integer> subscriber = Multi.createFrom().items(1, 2, 3)
                .onSubscribe()
                .call(s -> Uni.createFrom().emitter((Consumer<UniEmitter<? super Integer>>) emitter::set))
                .subscribe().withSubscriber(AssertSubscriber.create(3));

        subscriber.assertNotSubscribed();

        await().until(() -> emitter.get() != null);
        emitter.get().complete(12345);

        subscriber.await()
                .assertSubscribed()
                .assertCompletedSuccessfully().assertReceived(1, 2, 3);

    }

    @Test
    public void testThatSubscriptionIsNotPassedDownstreamUntilProducedUniCompletesWithDifferentThread() {
        AtomicReference<UniEmitter<? super Integer>> emitter = new AtomicReference<>();
        AssertSubscriber<Integer> subscriber = Multi.createFrom().items(1, 2, 3)
                .onSubscribe()
                .call(s -> Uni.createFrom().emitter((Consumer<UniEmitter<? super Integer>>) emitter::set))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().withSubscriber(AssertSubscriber.create(3));

        subscriber.assertNotSubscribed();

        await().until(() -> emitter.get() != null);
        emitter.get().complete(12345);

        subscriber.await()
                .assertSubscribed()
                .assertCompletedSuccessfully().assertReceived(1, 2, 3);

    }

    @Test
    public void testThatRunSubscriptionOnEmitRequestOnSubscribe() {
        AssertSubscriber<Integer> subscriber = Multi.createFrom().items(1, 2, 3)
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().withSubscriber(AssertSubscriber.create(2));

        subscriber
                .request(1)
                .await()
                .assertReceived(1, 2, 3)
                .assertCompletedSuccessfully();
    }

    @Test
    public void testRunSubscriptionOnShutdownExecutor() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.shutdownNow();

        AssertSubscriber<Integer> subscriber = Multi.createFrom().items(1, 2, 3)
                .runSubscriptionOn(executor)
                .subscribe().withSubscriber(AssertSubscriber.create(2));

        subscriber.assertHasFailedWith(RejectedExecutionException.class, "");
    }

    @Test
    public void testRunSubscriptionOnShutdownExecutorRequests() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        AssertSubscriber<Integer> subscriber = Multi.createFrom().items(1, 2, 3)
                .runSubscriptionOn(executor)
                .subscribe().withSubscriber(AssertSubscriber.create(0));

        await().untilAsserted(subscriber::assertSubscribed);

        subscriber.assertHasNotReceivedAnyItem();
        executor.shutdownNow();
        subscriber.request(2);

        subscriber.assertHasFailedWith(RejectedExecutionException.class, "");
    }

}
