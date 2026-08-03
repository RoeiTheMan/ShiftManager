package com.example.shiftmanager;

/**
 * What a screen hands to the repository so it can be told how a database call ended.
 *
 * Firestore calls are asynchronous: they finish some time after we ask, so there is
 * nothing useful to return from the method itself. Instead the screen passes one of
 * these in, and the repository calls back on success or on failure.
 *
 * @param <T> what comes back on success (a list of shifts, an id, and so on)
 */
public interface Callback<T> {

    void onSuccess(T result);

    void onError(Exception error);
}
