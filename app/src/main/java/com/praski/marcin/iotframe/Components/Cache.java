package com.praski.marcin.iotframe.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Pair;

import com.praski.marcin.iotframe.Util.Subscriber;
import com.praski.marcin.iotframe.Util.SubscriberAsyncTask;
import com.praski.marcin.iotframe.Util.Subscription;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Created by marcin on 07.09.17.
 */

public class Cache {
    private static final String CACHE_PATH = "thumbnails";
    private final Object mCacheLock = new Object();
    private File mCachePath;

    private Cache() {
    }

    public static Cache getInstance() {
        return Cache.InstanceHolder.mCache;
    }

    public void initializeCache(Context context) {
        mCachePath = getDiskCacheDir(context, CACHE_PATH);
    }

    public void saveBitmap(String key, Bitmap bitmap) {
        new FileSaver().execute(new Pair<>(key, bitmap));
    }

    public Subscription getBitmap(Subscriber<Bitmap> subscriber, String key) {
        SubscriberAsyncTask<String, Void, Bitmap> task = new FileLoader()
                .subscribe(subscriber);

        task.execute(key);

        return task.getSubscription();
    }

    public boolean fileExists(String key) {
        File file = new File(mCachePath, key + ".jpeg");
        return file.exists();
    }

    private File getDiskCacheDir(Context context, String uniqueName) {
        File file = new File(context.getCacheDir().getPath() + File.separator + uniqueName);
        if (!file.exists()) file.mkdirs();

        return file;
    }

    private static class InstanceHolder {
        private static Cache mCache = new Cache();
    }

    private class FileLoader extends SubscriberAsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... strings) {
            String path = strings[0];

            synchronized (mCacheLock) {
                File file = new File(mCachePath, path + ".jpeg");
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            subscriber.onNext(result);
            subscriber.onComplete();
        }
    }

    private class FileSaver extends AsyncTask<Pair<String, Bitmap>, Void, Void> {

        @Override
        protected Void doInBackground(Pair<String, Bitmap>... data) {
            Pair<String, Bitmap> item = data[0];

            synchronized (mCacheLock) {
                File file = new File(mCachePath, item.first + ".jpeg");
                if (file.exists())
                    file.delete();
                try {
                    FileOutputStream out = new FileOutputStream(file);
                    item.second.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    out.flush();
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return null;
        }
    }
}
