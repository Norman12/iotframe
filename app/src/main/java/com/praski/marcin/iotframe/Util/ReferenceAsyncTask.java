package com.praski.marcin.iotframe.Util;

import android.os.AsyncTask;

import java.util.Set;

/**
 * Created by marcin on 07.09.17.
 */

public abstract class ReferenceAsyncTask<T, U, V> extends AsyncTask<T, U, V> {
    private Set<AsyncTask> set;

    public ReferenceAsyncTask<T, U, V> references(Set<AsyncTask> set) {
        this.set = set;
        return this;
    }

    @Override
    protected void onPreExecute() {
        if (set != null)
            set.add(this);

        super.onPreExecute();
    }

    @Override
    protected void onPostExecute(V result) {
        super.onPostExecute(result);

        if (set != null)
            set.remove(this);
    }
}