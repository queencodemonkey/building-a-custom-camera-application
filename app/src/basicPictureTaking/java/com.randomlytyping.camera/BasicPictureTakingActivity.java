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
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;

/**
 * The BasicPictureTakingActivity class is just a simple example of how to start using a device
 * camera with {@link android.hardware.Camera} instance and start a camera preview with a {@link
 * android.view.SurfaceView} and a {@link android.view.SurfaceHolder}.
 */
public class BasicPictureTakingActivity extends Activity implements SurfaceHolder.Callback,
        View.OnClickListener {
    // Views
    private TextView mErrorTextView;
    private SurfaceView mPreviewSurface;
    private ImageView mPictureView;
    private ImageButton mCaptureButton;

    // Camera fields
    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;

    // Threading/runnables
    private long mHidePictureDelay;
    private Handler mHandler;
    private Runnable mHidePictureRunnable = new Runnable() {
        @Override
        public void run() {
            hidePictureTaken();
        }
    };

    // Picture callbacks
    private final Camera.PictureCallback mRawCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            showPicture(PictureUtils.bitmapFromRawBytes(
                    data,
                    mPictureView.getWidth(),
                    mPictureView.getHeight()
            ));
        }
    };

    //
    // Activity lifecycle
    //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_basic_picture_taking);

        // Grab references to views and set them up.
        mPreviewSurface = (SurfaceView) findViewById(R.id.preview_surface);
        mErrorTextView = (TextView) findViewById(R.id.error_text);
        mPictureView = (ImageView) findViewById(R.id.picture_taken);
        mCaptureButton = (ImageButton) findViewById(R.id.capture_button);
        mCaptureButton.setOnClickListener(this);

        // Initialize fields for executing code on the UI thread.
        mHidePictureDelay = getResources().getInteger(R.integer.picture_taken_show_duration);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If there is a hardware camera then open it and start setting up the preview surface.
        if (hasCamera()) {
           openCamera();
            mSurfaceHolder = mPreviewSurface != null ? mPreviewSurface.getHolder() : null;
            if (mSurfaceHolder != null) {
                /*  Add a callback to the SurfaceHolder so that we can start the preview after the
                surface is created. */
                mSurfaceHolder.addCallback(this);

                /*  In order to support Gingerbread and below, need to call SurfaceHolder#setType.
                If Gingerbread support is not needed, then do not call #setType as it is deprecated
                and higher API levels take care of this setting automatically. */
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                    mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                }

                hideError();
            } else {
                showError(R.string.error_preview_surface_view_does_not_exist);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Tear down the surface holder.
        if (mSurfaceHolder != null) {
            mSurfaceHolder.removeCallback(this);
        }

        // Close the camera while we are not using so that other applications can use it.
        closeCamera();

        // Hide any shown picture so that when we come back the camera and preview are shown.
        mHandler.removeCallbacks(mHidePictureRunnable);
        hidePictureTaken();
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
        if ( mCamera == null ) {
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
     * Starts the camera preview using our already-created surface or shows an error message if
     * there was a problem starting the preview.
     */
    private void startPreview() {
        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.startPreview();
        } catch (IOException e) {
            showError(R.string.error_preview_not_started);
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
        /*  Re-open the camera in the case that the surface is being re-created meaning that the
        camera was closed when it was previously destroyed. */
        openCamera();

        // The surface was created so try to start the camera preview displaying on the surface.
        startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (holder.getSurface() == null) {
            showError(R.string.error_surface_does_not_exist);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // If the surface is destroyed, close the camera.
        closeCamera();
    }


    //
    // View.OnClickListener implementation
    //

    @Override
    public void onClick(View v) {
        if (mCamera != null) {
            // Take picture and capture raw image data.
            mCamera.takePicture(null, null, mRawCallback);
        }
    }
}
