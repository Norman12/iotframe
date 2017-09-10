package com.praski.marcin.iotframe.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import com.praski.marcin.iotframe.Http.GetRequest;
import com.praski.marcin.iotframe.Http.Interceptor;
import com.praski.marcin.iotframe.Http.PostRequest;
import com.praski.marcin.iotframe.Models.ImageRequest;
import com.praski.marcin.iotframe.Models.ImageResponse;
import com.praski.marcin.iotframe.Util.Subscriber;
import com.praski.marcin.iotframe.Util.SubscriberAsyncTask;
import com.praski.marcin.iotframe.Util.Subscription;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by marcin on 07.09.17.
 */

public class Service {
    private final static long POLL_INTERVAL = 10000;
    private final static long POLL_DELAY = 5000;
    private final static String API_GET_UUID = "uuid";
    private final static String API_GET_IMAGE = "image";
    private final static String API_GET_SEEN = "seen";
    private final static String API_POST_IMAGE = "image/post";
    private final static String API_POST_SEEN = "seen/post";
    private final Map<String, String> headers = new HashMap<>();
    private String root = "";
    private Interceptor interceptor;

    private Service() {
    }

    public static Service getInstance() {
        return InstanceHolder.mService;
    }

    public void setInterceptor(Interceptor interceptor) {
        this.interceptor = interceptor;
    }

    public void setServiceRoot(String root) {
        this.root = root;
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public void removeHeader(String key) {
        headers.remove(key);
    }

    public Subscription getImageUpdates(final Subscriber<ImageResponse> subscriber) {
        final Handler handler = new Handler();
        final Timer timer = new Timer();
        final Set<AsyncTask> tasks = new HashSet<>();

        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        new GetRequest<>(ImageResponse.class)
                                .headers(headers)
                                .interceptor(interceptor)
                                .subscribe(subscriber)
                                .references(tasks)
                                .execute(root + API_GET_IMAGE);
                    }
                });
            }
        };

        timer.schedule(task, POLL_DELAY, POLL_INTERVAL);

        return new Subscription() {
            @Override
            public void unsubscribe() {
                timer.cancel();

                for (AsyncTask a : tasks) {
                    if (a != null && !a.isCancelled()) {
                        a.cancel(true);
                    }
                }
            }
        };
    }

    public Subscription getSeenUpdates(final Subscriber<Boolean> subscriber) {
        final Handler handler = new Handler();
        final Timer timer = new Timer();
        final Set<AsyncTask> tasks = new HashSet<>();

        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        new GetRequest<>(Boolean.class)
                                .headers(headers)
                                .interceptor(interceptor)
                                .subscribe(subscriber)
                                .references(tasks)
                                .execute(root + API_GET_SEEN);
                    }
                });
            }
        };

        timer.schedule(task, POLL_DELAY, POLL_INTERVAL);

        return new Subscription() {
            @Override
            public void unsubscribe() {
                timer.cancel();

                for (AsyncTask a : tasks) {
                    if (a != null && !a.isCancelled()) {
                        a.cancel(true);
                    }
                }
            }
        };
    }

    public Subscription downloadImage(Subscriber<Bitmap> subscriber, String url) {
        final SubscriberAsyncTask<String, Void, Bitmap> task = new ImageLoader()
                .subscribe(subscriber);

        task.execute(url);

        return task.getSubscription();
    }

    public Subscription getUuid(Subscriber<String> subscriber) {
        final SubscriberAsyncTask<String, Void, String> task = new GetRequest<>(String.class)
                .headers(headers)
                .subscribe(subscriber);

        Log.d("Service", "URL: <" + root + API_GET_UUID + ">");

        task.execute(root + API_GET_UUID);

        return task.getSubscription();
    }

    public Subscription uploadImage(Subscriber<Boolean> subscriber, ImageRequest request) {
        final SubscriberAsyncTask<String, Void, Boolean> task = new PostRequest<>(Boolean.class)
                .headers(headers)
                .body(request, ImageRequest.class)
                .interceptor(interceptor)
                .subscribe(subscriber);

        task.execute(root + API_POST_IMAGE);

        return task.getSubscription();
    }

    public Subscription postSeen(Subscriber<Boolean> subscriber) {
        final SubscriberAsyncTask<String, Void, Boolean> task = new PostRequest<>(Boolean.class)
                .headers(headers)
                .interceptor(interceptor)
                .subscribe(subscriber);

        task.execute(root + API_POST_SEEN);

        return task.getSubscription();
    }

    private static class InstanceHolder {
        private static Service mService = new Service();
    }

    private class ImageLoader extends SubscriberAsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... params) {
            try {
                return downloadBitmap(params[0]);
            } catch (Exception e) {
                subscriber.onError(e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }

            if (bitmap != null) {
                subscriber.onNext(bitmap);
            } else {
                subscriber.onError(new Exception("bitmap is null"));
            }

            subscriber.onComplete();
        }

        private Bitmap downloadBitmap(String url) {
            HttpURLConnection urlConnection = null;
            try {
                URL uri = new URL(url);
                urlConnection = (HttpURLConnection) uri.openConnection();
                int statusCode = urlConnection.getResponseCode();
                if (statusCode != HttpURLConnection.HTTP_OK) {
                    return null;
                }

                InputStream inputStream = urlConnection.getInputStream();
                if (inputStream != null) {
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    return bitmap;
                }
            } catch (Exception e) {
                urlConnection.disconnect();
                Log.w("ImageDownloader", "Error downloading image from " + url);
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
            return null;
        }
    }
}
