package com.praski.marcin.iotframe;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.praski.marcin.iotframe.Components.Cache;
import com.praski.marcin.iotframe.Components.Camera;
import com.praski.marcin.iotframe.Components.Persistence;
import com.praski.marcin.iotframe.Components.Service;
import com.praski.marcin.iotframe.Http.Interceptor;
import com.praski.marcin.iotframe.Models.ImageRequest;
import com.praski.marcin.iotframe.Models.ImageResponse;
import com.praski.marcin.iotframe.Util.Subscriber;
import com.praski.marcin.iotframe.Util.Subscription;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {
    private final static String HEADER_KEY = "Key";
    private final static String HEADER_UUID = "Uuid";
    private final static String TAG = MainActivity.class.getSimpleName();
    private final Object mViewLock = new Object();
    private final List<Subscription> mSubscriptions = new ArrayList<>();
    private final Calendar mLast = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    private final Calendar mUploaded = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    private final int[] mColors = {
            R.color.colorBrandOne,
            R.color.colorBrandTwo,
            R.color.colorBrandThree
    };
    private final int mSteps = 3;
    private final long mCountdown = 3000;
    private final long mInterval = 1000;
    /**
     * Subscriber representing the state of image upload request
     */
    private Subscriber<Boolean> mImageUploadSubscriber;
    /**
     * Subscriber representing periodic checking if a the other side saw your image
     */
    private Subscriber<Boolean> mSeenUpdateSubscriber;
    private Service mService;
    private Persistence mPersistence;
    private Camera mCamera;
    private Cache mCache;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();

                    ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
                    final byte[] imageBytes = new byte[imageBuf.remaining()];
                    imageBuf.get(imageBytes);
                    image.close();

                    onPictureTaken(imageBytes);
                }
            };
    private boolean mIsReady, mIsDownloading, mIsUploading, mIsRunning, mIsAlertShowing, mIsAuthenticating;
    /**
     * Subscriber representing the authentication request
     */
    private Subscriber<String> mUuidSubscriber;
    private ImageView mImageMain, mImagePreview, mImageSeen;
    /**
     * Subscriber representing the image download request
     */
    private Subscriber<Bitmap> mImageDownloadSubscriber;
    /**
     * Subscriber for receiving periodic checking if a new image is available
     */
    private Subscriber<ImageResponse> mImageUpdateSubscriber;
    private TextView mTextCheese, mTextSeen;
    private View mButtonCamera, mButtonSeen;
    private Handler mCameraHandler;
    private HandlerThread mCameraThread;
    private int mCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            showAlert(new Exception("No camera permission present"));
            return;
        }

        mImageMain = (ImageView) findViewById(R.id.image_main);
        mImagePreview = (ImageView) findViewById(R.id.image_preview);
        mImageSeen = (ImageView) findViewById(R.id.image_seen);

        mTextCheese = (TextView) findViewById(R.id.text_cheese);
        mTextSeen = (TextView) findViewById(R.id.text_seen);

        mButtonCamera = findViewById(R.id.button_camera);
        mButtonSeen = findViewById(R.id.button_seen);

        mButtonCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mIsReady)
                    takePicture();
            }
        });

        mButtonSeen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mIsReady)
                    seenPicture();
            }
        });

        mService = Service.getInstance();

        mPersistence = Persistence.getInstance();
        mPersistence.initializeStorage(getApplicationContext());

        mCache = Cache.getInstance();
        mCache.initializeCache(this);

        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mCamera = Camera.getInstance();
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener);

        initializeSubscribers();

        initializeNetwork();

        loadCachedImages();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mCamera.shutDown();
        mCameraThread.quitSafely();

        for (Subscription s : mSubscriptions) {
            s.unsubscribe();
        }

        mIsRunning = false;
    }

    private void onPictureTaken(final byte[] bytes) {
        if (bytes != null) {
            ImageRequest request = new ImageRequest();
            request.setMime("image/jpeg");
            request.setData(Base64.encodeToString(bytes, Base64.DEFAULT));

            mIsUploading = true;
            mService.uploadImage(mImageUploadSubscriber, request);

            final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateImageBitmap(bitmap, mImagePreview);
                }
            });

            mCache.saveBitmap("your", bitmap);
        }
    }

    private void takePicture() {
        if (mIsUploading) {
            showAlert(new Exception("Please wait, the previous image is being mUploaded."));
            return;
        }

        mTextCheese.setVisibility(View.VISIBLE);
        mCounter = 0;

        new CountDownTimer(mCountdown, mInterval) {
            @Override
            public void onTick(long l) {
                mTextCheese.setText(String.valueOf(mSteps - mCounter));
                mTextCheese.setBackgroundColor(ResourcesCompat.getColor(getResources(), mColors[mCounter], getTheme()));

                mCounter++;
            }

            @Override
            public void onFinish() {
                mTextCheese.setText("Cheese!");

                mCamera.takePicture();

                new android.os.Handler().postDelayed(
                        new Runnable() {
                            public void run() {
                                mTextCheese.setVisibility(View.GONE);
                                mTextCheese.setText("3");
                                mTextCheese.setBackgroundColor(ResourcesCompat.getColor(getResources(), mColors[0], getTheme()));
                            }
                        }, 1000);
            }
        }.start();
    }

    private void seenPicture() {
        mService.postSeen(new Subscriber<Boolean>() {
            @Override
            public void onNext(Boolean aBoolean) {

            }

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Exception e) {
                showAlert(e);
                Log.e(TAG, "Failed to update seen", e);
            }
        });
        mTextSeen.setVisibility(View.VISIBLE);
        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        mTextSeen.setVisibility(View.GONE);
                    }
                }, 2000);
    }

    private void initializeSubscribers() {
        mImageUploadSubscriber = new Subscriber<Boolean>() {
            @Override
            public void onNext(Boolean aBoolean) {

            }

            @Override
            public void onComplete() {
                mIsUploading = false;
            }

            @Override
            public void onError(Exception e) {
                showAlert(e);
                Log.e(TAG, "Failed to upload image", e);
            }
        };
        mSeenUpdateSubscriber = new Subscriber<Boolean>() {
            @Override
            public void onNext(Boolean aBoolean) {
                synchronized (mViewLock) {
                    Log.d(TAG, "Updating seen notification");
                    mImageSeen.setVisibility(aBoolean ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to update seen", e);
            }
        };
        mUuidSubscriber = new Subscriber<String>() {
            @Override
            public void onNext(String s) {
                mPersistence.setUuid(s);

                mService.removeHeader(HEADER_KEY);
                mService.addHeader(HEADER_UUID, s);

                mIsReady = true;
                mIsAuthenticating = false;

                Log.d(TAG, "Received UUID: " + s);
            }

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to obtain UUID", e);
            }
        };
        mImageDownloadSubscriber = new Subscriber<Bitmap>() {
            @Override
            public void onNext(Bitmap bitmap) {
                updateImageBitmap(bitmap, mImageMain);
                mCache.saveBitmap("main", bitmap);

                Log.d(TAG, "Downloaded and cached image");
            }

            @Override
            public void onComplete() {
                mIsDownloading = false;
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to download image", e);
            }
        };
        mImageUpdateSubscriber = new Subscriber<ImageResponse>() {
            @Override
            public void onNext(ImageResponse imageResponse) {
                if (mIsDownloading)
                    return;

                Log.d(TAG, "Trying to update image");

                mUploaded.setTime(imageResponse.getDate());

                if (mUploaded.after(mLast)) {
                    mLast.setTime(imageResponse.getDate());

                    mIsDownloading = true;
                    mService.downloadImage(mImageDownloadSubscriber, imageResponse.getUrl());

                    Log.d(TAG, "Updated image with url: " + imageResponse.getUrl());
                }
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to update image", e);
            }
        };
    }

    private void initializeNetwork() {
        Log.d(TAG, "Initiating network");

        mService.setServiceRoot(getString(R.string.api_root));
        mService.setInterceptor(new Interceptor() {
            @Override
            public void unauthorized() {
                if (!mIsAuthenticating) {
                    Log.d(TAG, "Authenticating...");

                    mIsReady = false;
                    mIsAuthenticating = true;

                    mPersistence.deleteUuid();

                    tryToAuthenticate();
                }
            }
        });

        mIsReady = mPersistence.hasUuid();

        Log.d(TAG, "UUID present: " + mIsReady);

        if (!mIsRunning) {
            Log.d(TAG, "Started polling");

            startPolling();
            mIsRunning = true;
        }
    }

    private void tryToAuthenticate() {
        mService.addHeader(HEADER_KEY,
                getString(R.string.api_key));

        mService.getUuid(mUuidSubscriber);
    }

    private void startPolling() {
        mService.addHeader(HEADER_UUID,
                mPersistence.getUuid());

        mSubscriptions.add(
                mService.getImageUpdates(mImageUpdateSubscriber));

        mSubscriptions.add(
                mService.getSeenUpdates(mSeenUpdateSubscriber));
    }

    private void showAlert(final Exception e) {
        if (!mIsAlertShowing) {
            mIsAlertShowing = true;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                            .setMessage(e.getMessage())
                            .setTitle("Error")
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialogInterface) {
                                    mIsAlertShowing = false;
                                }
                            })
                            .create();

                    dialog.show();
                }
            });
        }
    }

    private void updateImageBitmap(Bitmap bitmap, ImageView imageView) {
        if (imageView.getDrawable() instanceof BitmapDrawable) {
            ((BitmapDrawable) imageView.getDrawable()).getBitmap().recycle();
        }
        imageView.setImageBitmap(bitmap);
    }

    private void loadCachedImages() {
        if (mCache.fileExists("main"))
            mCache.getBitmap(new Subscriber<Bitmap>() {
                @Override
                public void onNext(final Bitmap bitmap) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateImageBitmap(bitmap, mImageMain);
                        }
                    });
                }

                @Override
                public void onComplete() {

                }

                @Override
                public void onError(Exception e) {

                }
            }, "main");

        if (mCache.fileExists("your"))
            mCache.getBitmap(new Subscriber<Bitmap>() {
                @Override
                public void onNext(final Bitmap bitmap) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateImageBitmap(bitmap, mImagePreview);
                        }
                    });
                }

                @Override
                public void onComplete() {

                }

                @Override
                public void onError(Exception e) {

                }
            }, "your");
    }
}
