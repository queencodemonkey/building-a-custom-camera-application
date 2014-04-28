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
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;

/**
 * The BasicPictureTakingActivity class is just a simple example of how to start using a device camera
 * with {@link android.hardware.Camera} instance and start a camera preview with a
 * {@link android.view.SurfaceView} and a {@link android.view.SurfaceHolder}.
 */
public class BasicPictureTakingActivity extends Activity implements SurfaceHolder.Callback {
    // Views
    private TextView mErrorTextView;
    private SurfaceView mPreviewSurfaceView;
    private ImageView mTakenPhotoView;

    // Camera fields
    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;

    // Picture callbacks
    private final Camera.PictureCallback rawCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            mTakenPhotoView.setImageBitmap(PhotoUtils.bitmapFromRawBytes(
                    data,
                    mTakenPhotoView.getWidth(),
                    mTakenPhotoView.getHeight()
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

        // Grab references to the SurfaceView for the preview and the TextView for display errors.
        mPreviewSurfaceView = (SurfaceView) findViewById(R.id.preview_surface);
        mErrorTextView = (TextView) findViewById(R.id.error_text);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If there is a hardware camera then open it and start setting up the preview surface.
        if (hasCamera()) {
            mCamera = openCamera();
            mSurfaceHolder = mPreviewSurfaceView != null ? mPreviewSurfaceView.getHolder() : null;
            if ( mSurfaceHolder != null )
            {
                /*  Add a callback to the SurfaceHolder so that we can start the preview after the
                surface is created. */
                mSurfaceHolder.addCallback( this );

                /*  In order to support Gingerbread and below, need to call SurfaceHolder#setType.
                If Gingerbread support is not needed, then do not call #setType as it is deprecated
                and higher API levels take care of this setting automatically. */
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB ) {
                    mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                }

                hideError();
            }
            else
            {
                showError(R.string.error_preview_surface_view_does_not_exist);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Tear down the surface holder.
        if (mSurfaceHolder != null)
        {
            mSurfaceHolder.removeCallback(this);
        }

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
     *
     * @return A {@link android.hardware.Camera} instance for setting up the device camera and
     * taking pictures.
     */
    private Camera openCamera() {
        return Camera.open();
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
        try {

            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.startPreview();
        }
        catch (IOException e) {
            showError(R.string.error_preview_not_started);
        }
    }


    //
    // Show/hide error text
    //

    /**
     * Show an error message text and hide the camera preview.
     *
     * @param errorResourceId A resource ID for the error string resource.
     */
    private void showError(int errorResourceId)
    {
        mErrorTextView.setText(errorResourceId);
        mErrorTextView.setVisibility(View.VISIBLE);
        mPreviewSurfaceView.setVisibility(View.GONE);
    }

    /**
     * Hides the error message text and show the camera preview.
     */
    private void hideError()
    {
        mErrorTextView.setVisibility(View.GONE);
        mPreviewSurfaceView.setVisibility(View.VISIBLE);
    }


    //
    // SurfaceHolder.Callback implementation
    //

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // The surface was created so try to start the camera preview displaying on the surface.
        startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (holder.getSurface() == null)
        {
            showError(R.string.error_surface_does_not_exist);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // If the surface is destroyed, close the camera.
        closeCamera();
    }
}
