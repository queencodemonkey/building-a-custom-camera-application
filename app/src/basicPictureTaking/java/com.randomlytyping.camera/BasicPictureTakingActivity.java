/*
 * Copyright 2014 Randomly Typing LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.randomlytyping.camera;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.lang.Override;

/**
 * The BasicPictureTakingActivity class demonstrates simple picture taking with the {@link
 * android.hardware.Camera}. Along with actually taking the picture, the BasicPictureTakingActivity
 * may display the taken picture to the user for a brief period of time.
 * <p/>
 * Created by Huyen Tue Dao on 04/28/14.
 */
public class BasicPictureTakingActivity extends Activity implements SurfaceHolder.Callback,
        View.OnClickListener {

    /**
     * Class tag for logging.
     */
    @SuppressWarnings("unused")
    private static final String TAG = "BasicPictureTakingActivity";

    // Views
    private SurfaceView mPreviewSurface;
    private TextView mErrorTextView;

    private ImageView mPictureView;
    private ImageButton mCaptureButton;

    // Camera fields
    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;

    // Flags
    private boolean canStartPreview;

    // Threading/runnables
    private long mHidePictureDelay;
    private Handler mHandler;
    private Runnable mHidePictureRunnable = new Runnable() {
        @Override
        public void run() {
            hidePictureTaken();
        }
    };


    //
    // Activity lifecycle
    //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_basic_picture_taking);

        // Grab references to the SurfaceView for the preview and the TextView for display errors.
        mPreviewSurface = (SurfaceView) findViewById(R.id.preview_surface);
        mErrorTextView = (TextView) findViewById(R.id.error_text);

        // Grab references to the picture-taking-related views.
        mPictureView = (ImageView) findViewById(R.id.picture_taken);
        mCaptureButton = (ImageButton) findViewById(R.id.capture_button);

        /*
            If the device actually has a camera, set up the surface holder.
            Otherwise, display an error message.
        */
        if (hasCamera()) {
            /*
                If the activity contains a valid SurfaceView for the preview, then set it up.
                Otherwise, display an error message.
             */
            if (mPreviewSurface != null) {
                /*  Need to grab a reference to the SurfaceHolder so that we can respond to changes
                in the surface. */
                mSurfaceHolder = mPreviewSurface.getHolder();

                /*  Add a callback to the SurfaceHolder so that we can start the preview after the
                surface is created. */
                mSurfaceHolder.addCallback(this);

                /*  In order to support Gingerbread and below, need to call SurfaceHolder#setType.
                If Gingerbread support is not needed, then do not call #setType as it is deprecated
                and higher API levels take care of this setting automatically. */
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                    mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                }

                /*  Initialize fields for executing code on the UI thread. We are using this for
                hiding the preview of a taken picture after a short delay. */
                mHidePictureDelay = getResources().getInteger(R.integer.picture_taken_show_duration);
                mHandler = new Handler(Looper.getMainLooper());
                mCaptureButton.setOnClickListener(this);

                hideError();
            } else {
                /*  Disable picture-taking button if there is no camera preview since we cannot take
                pictures with the Camera if there is no preview. */
                mCaptureButton.setEnabled(false);

                showError(R.string.error_preview_surface_view_does_not_exist);
            }
        } else {
            showError(R.string.error_no_camera);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If there is a hardware camera then open it and start setting up the preview surface.
        if (mPreviewSurface != null && hasCamera()) {
            openCamera();
            // If the surface has already been created, then start the preview.
            if (canStartPreview) {
                startPreview();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Close the camera while we are not using so that other applications can use it.
        closeCamera();
    }


    //
    // Camera setup
    //

    /**
     * Check whether the device actually has a camera.
     *
     * @return True if the device has a camera, false otherwise.
     */
    private boolean hasCamera() {
        final PackageManager packageManager = getPackageManager();
        return packageManager != null
                && packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    /**
     * Open the first back-facing camera and grab a {@link android.hardware.Camera} instance.
     */
    private void openCamera() {
        if (mCamera == null) {
            mCamera = Camera.open();
        }
    }

    /**
     * Close the camera and release the previously obtained {@link android.hardware.Camera} instance
     * to make sure that other applications can grab the camera if needed.
     */
    private void closeCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * Starts the camera preview using our already-created surface.
     */
    private void startPreview() {
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mCamera.startPreview();
            } catch (IOException e) {
                showError(R.string.error_preview_not_started);
            }
        }
    }


    //
    // Picture taking
    //

    /**
     * Callback for retrieving JPEG data when a picture is taken.
     */
    private final Camera.PictureCallback mJpegCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, String.format("JPEG Callback"));
            if (BuildConfig.SHOW_PICTURE) {
                showPicture(CameraUtils.bitmapFromRawBytes(
                        data,
                        mPictureView.getWidth(),
                        mPictureView.getHeight()
                ));
            }
        }
    };

    /**
     * Checks if the camera is open and takes a picture, retrieving JPEG data.
     */
    private void takePicture() {
        if (mCamera != null) {
            // Take picture and capture JPEG image data.
            mCamera.takePicture(null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    Log.d(TAG, String.format("onPictureTaken:RAW DATA?"));
                }
            }, mJpegCallback);
        }
    }


    //
    // Show/hide picture taken
    //

    /**
     * Displays a picture taken with the {@link android.hardware.Camera} and hides the preview and
     * capture button.
     *
     * @param bitmap A {@link android.graphics.Bitmap} containing the picture taken.
     */
    private void showPicture(Bitmap bitmap) {
        mPictureView.setImageBitmap(bitmap);
        mPictureView.setVisibility(View.VISIBLE);
        mPreviewSurface.setVisibility(View.INVISIBLE);
        mCaptureButton.setVisibility(View.GONE);
        mHandler.postDelayed(mHidePictureRunnable, mHidePictureDelay);
    }

    /**
     * Hides any shown picture and shows the preview and capture button.
     */
    private void hidePictureTaken() {
        mPictureView.setVisibility(View.INVISIBLE);
        mPreviewSurface.setVisibility(View.VISIBLE);
        mCaptureButton.setVisibility(View.VISIBLE);
    }


    //
    // Show/hide error text
    //

    /**
     * Show an error message text and hide the camera preview.
     *
     * @param errorResourceId A resource ID for the error string resource.
     */
    private void showError(int errorResourceId) {
        mErrorTextView.setText(errorResourceId);
        mErrorTextView.setVisibility(View.VISIBLE);
        mPreviewSurface.setVisibility(View.GONE);
    }

    /**
     * Hides the error message text and show the camera preview.
     */
    private void hideError() {
        mErrorTextView.setVisibility(View.GONE);
        mPreviewSurface.setVisibility(View.VISIBLE);
    }


    //
    // SurfaceHolder.Callback implementation
    //

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        canStartPreview = true;

        // The surface was created so try to start the camera preview displaying on the surface.
        startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // If the surface is destroyed, stop the camera preview.
        if (mCamera != null) {
            mCamera.stopPreview();
        }

        canStartPreview = false;
    }


    //
    // View.OnClickListener implementation
    //

    @Override
    public void onClick(View v) {
        takePicture();
    }
}
