package com.praski.marcin.iotframe.Util;

/**
 * Created by marcin on 07.09.17.
 */

public abstract class SubscriberAsyncTask<T, U, V> extends ReferenceAsyncTask<T, U, V> {
    private final Subscription subscription = new Subscription() {
        @Override
        public void unsubscribe() {
            cancel(true);
        }
    };

    protected Subscriber<V> subscriber;

    public Subscription getSubscription() {
        return subscription;
    }

    public SubscriberAsyncTask<T, U, V> subscribe(Subscriber<V> subscriber) {
        this.subscriber = subscriber;
        return this;
    }
}
