package com.praski.marcin.iotframe.Util;

/**
 * Created by marcin on 07.09.17.
 */

public interface Subscriber<T> {
    void onNext(T t);

    void onComplete();

    void onError(Exception e);
}
