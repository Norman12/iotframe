package com.praski.marcin.iotframe.Http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.praski.marcin.iotframe.Util.SubscriberAsyncTask;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * Created by marcin on 07.09.17.
 */

public class PostRequest<T> extends SubscriberAsyncTask<String, Void, T> {
    public static final String REQUEST_METHOD = "POST";
    public static final String REQUEST_ACCEPT = "application/json";
    public static final String REQUEST_CONTENT_TYPE = "application/json";
    public static final int READ_TIMEOUT = 15000;
    public static final int CONNECTION_TIMEOUT = 15000;
    private final Gson gson;
    private final Class<T> clazz;
    private Interceptor mInterceptor;
    private Map<String, String> mHeaders;
    private String body;
    private HttpURLConnection mConnection;
    private Reader mReader;

    public PostRequest(Class<T> clazz) {
        this.clazz = clazz;
        this.gson = new GsonBuilder().create();
    }

    public PostRequest<T> headers(Map<String, String> headers) {
        this.mHeaders = headers;
        return this;
    }

    public <U> PostRequest<T> body(U body, Class<U> clazz) {
        this.body = gson.toJson(body, clazz);
        return this;
    }

    public PostRequest<T> interceptor(Interceptor interceptor) {
        this.mInterceptor = interceptor;
        return this;
    }

    @Override
    protected T doInBackground(String... params) {
        String url = params[0];

        Response<T> response = null;
        T result = null;

        try {
            URL myUrl = new URL(url);

            mConnection = (HttpURLConnection)
                    myUrl.openConnection();

            mConnection.setDoOutput(true);
            mConnection.setDoInput(true);

            mConnection.setRequestMethod(REQUEST_METHOD);
            mConnection.setReadTimeout(READ_TIMEOUT);
            mConnection.setConnectTimeout(CONNECTION_TIMEOUT);

            mConnection.setRequestProperty("Content-Type", REQUEST_CONTENT_TYPE);
            mConnection.setRequestProperty("Accept", REQUEST_ACCEPT);

            if (mHeaders != null)
                for (Map.Entry<String, String> entry : mHeaders.entrySet()) {
                    mConnection.setRequestProperty(entry.getKey(), entry.getValue());
                }

            mConnection.connect();

            if (body != null && !body.isEmpty()) {
                OutputStream os = mConnection.getOutputStream();
                OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");

                osw.write(body);
                osw.flush();
                osw.close();
            }

            int code = mConnection.getResponseCode();
            switch (code) {
                case HttpURLConnection.HTTP_OK:
                    break;
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                case HttpURLConnection.HTTP_FORBIDDEN:
                    if (mInterceptor != null)
                        mInterceptor.unauthorized();
                default:
                    subscriber.onError(new Exception("Code: " + code));
            }

            mReader = new
                    InputStreamReader(mConnection.getInputStream());

            response = gson.fromJson(mReader, Response.getType(clazz));

            mReader.close();
        } catch (IOException e) {
            subscriber.onError(e);
        } finally {
            if (mConnection != null)
                mConnection.disconnect();
        }

        if (response != null) {
            if (response.getError() == null) {
                result = response.getContent();
            } else {
                subscriber.onError(new Exception(response.getError()));
            }
        }

        return result;
    }

    protected void onPostExecute(T result) {
        if (result != null)
            subscriber.onNext(result);

        subscriber.onComplete();
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();

        if (mReader != null)
            try {
                mReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        if (mConnection != null)
            mConnection.disconnect();
    }
}
