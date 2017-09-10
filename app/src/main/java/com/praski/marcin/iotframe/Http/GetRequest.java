package com.praski.marcin.iotframe.Http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.praski.marcin.iotframe.Util.SubscriberAsyncTask;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Created by marcin on 07.09.17.
 */

public class GetRequest<T> extends SubscriberAsyncTask<String, Void, T> {
    public static final String REQUEST_METHOD = "GET";
    public static final String REQUEST_ACCEPT = "application/json";
    public static final int READ_TIMEOUT = 15000;
    public static final int CONNECTION_TIMEOUT = 15000;
    private final Gson gson;
    private final Class<T> clazz;
    private Interceptor mInterceptor;
    private Map<String, String> mParameters, mHeaders;
    private HttpURLConnection mConnection;
    private Reader mReader;

    public GetRequest(Class<T> clazz) {
        this.clazz = clazz;
        this.gson = new GsonBuilder().create();
    }

    public GetRequest<T> parameters(Map<String, String> params) {
        this.mParameters = params;
        return this;
    }

    public GetRequest<T> headers(Map<String, String> headers) {
        this.mHeaders = headers;
        return this;
    }

    public GetRequest<T> interceptor(Interceptor interceptor) {
        this.mInterceptor = interceptor;
        return this;
    }

    @Override
    protected T doInBackground(String... params) {
        String url = params[0];

        Response<T> response = null;
        T result = null;

        try {
            URL myUrl;

            if (mParameters != null && !mParameters.isEmpty()) {
                myUrl = new URL(buildUrl(url));
            } else {
                myUrl = new URL(url);
            }

            mConnection = (HttpURLConnection)
                    myUrl.openConnection();

            mConnection.setDoInput(true);

            mConnection.setRequestMethod(REQUEST_METHOD);
            mConnection.setReadTimeout(READ_TIMEOUT);
            mConnection.setConnectTimeout(CONNECTION_TIMEOUT);

            mConnection.setRequestProperty("Accept", REQUEST_ACCEPT);

            if (mHeaders != null)
                for (Map.Entry<String, String> entry : mHeaders.entrySet()) {
                    mConnection.setRequestProperty(entry.getKey(), entry.getValue());
                }

            mConnection.connect();

            int code = mConnection.getResponseCode();
            switch (code) {
                case HttpURLConnection.HTTP_OK:
                    break;
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                case HttpURLConnection.HTTP_FORBIDDEN:
                    if (mInterceptor != null)
                        mInterceptor.unauthorized();
                    break;
                default:
                    subscriber.onError(new Exception("Code: " + code));
            }

            mReader = new
                    InputStreamReader(mConnection.getInputStream(), "UTF-8");

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

    private String buildUrl(String url) {
        if (mParameters == null || mParameters.size() == 0) {
            return url;
        }

        int i = 0;

        StringBuffer buffer = new StringBuffer();
        buffer.append(url);

        try {
            for (Map.Entry<String, String> entry : mParameters.entrySet()) {
                if (i == 0) {
                    buffer.append("?");
                } else {
                    buffer.append("&");
                }

                buffer.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                buffer.append("=");
                buffer.append(URLEncoder.encode(entry.getValue(), "UTF-8"));

                i++;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return buffer.toString();
    }


}