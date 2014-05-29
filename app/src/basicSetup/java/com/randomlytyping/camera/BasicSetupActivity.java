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

import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;

/**
 * The BasicSetupActivity class is just a simple example of how to start using a device camera with
 * {@link android.hardware.Camera} instance and start a simple camera preview with a {@link
 * android.view.SurfaceView} and a {@link android.view.SurfaceHolder}.
 * <p/>
 * Created by Huyen Tue Dao on 04/27/14.
 */
public class BasicSetupActivity extends Activity implements SurfaceHolder.Callback {
    // Views
    private SurfaceView mPreviewSurface;
    private TextView mErrorTextView;

    // Camera fields
    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;

    // Flags
    private boolean canStartPreview;


    //
    // Activity lifecycle
    //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_basic_setup);

        // Grab references to the SurfaceView for the preview and the TextView for display errors.
        mPreviewSurface = (SurfaceView) findViewById(R.id.preview_surface);
        mErrorTextView = (TextView) findViewById(R.id.error_text);

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

                hideError();
            } else {
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
}
