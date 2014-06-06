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
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Huyen Tue Dao on 5/13/14.
 */
public class CameraAreaActivity extends Activity implements View.OnClickListener,
        View.OnTouchListener, Preview.PreviewListener {
    /**
     * ID value for a particular camera (front or back) that was not found.
     */
    private static final int NO_CAMERA = -1;

    /**
     * Whether the currently open camera is the front-facing camera.
     */
    private static final String STATE_IS_FRONT_CAMERA = "isFrontCamera";

    /**
     * Class tag for logging.
     */
    @SuppressWarnings("unused")
    private static final String TAG = "CameraAreaActivity";

    // Views
    private TextView mErrorTextView;

    private ImageView mPictureView;
    private ImageButton mCaptureButton;

    private ImageButton mSwitchButton;

    private Preview mPreview;

    // Camera fields
    private Camera mCamera;
    private boolean mIsFrontCamera;
    private int mBackCameraId;
    private int mFrontCameraId;

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

        setContentView(R.layout.activity_camera_area);

        // Grab reference to the TextView for display errors.
        mErrorTextView = (TextView) findViewById(R.id.error_text);

        // Grab references to the picture-taking-related views.
        mPictureView = (ImageView) findViewById(R.id.picture_taken);
        mCaptureButton = (ImageButton) findViewById(R.id.capture_button);

        // Grab references to camera-switching-related views.
        mSwitchButton = (ImageButton) findViewById(R.id.switch_button);

        // Grab reference to the preview.
        mPreview = (Preview) findViewById(R.id.preview);
        mPreview.setListener(this);

        /*
            If the device actually has a camera, set up the surface holder.
            Otherwise, display an error message.
        */
        if (hasCamera()) {
            /*
                If the activity contains a valid SurfaceView for the preview, then set it up.
                Otherwise, display an error message.
             */
            if (mPreview != null) {
                /*  Initialize fields for executing code on the UI thread. We are using this for
                hiding the preview of a taken picture after a short delay. */
                mHidePictureDelay = getResources().getInteger(R.integer.picture_taken_show_duration);
                mHandler = new Handler(Looper.getMainLooper());

                // Get back-facing camera info.
                mBackCameraId = findCameraId(false);

                // If the device has a front-facing camera, determine what camera we open first.
                if (hasFrontCamera()) {
                    mFrontCameraId = findCameraId(true);
                    mIsFrontCamera = savedInstanceState != null
                            && savedInstanceState.getBoolean(STATE_IS_FRONT_CAMERA, false);
                    mSwitchButton.setOnClickListener(this);
                } else {
                    mFrontCameraId = NO_CAMERA;
                    mSwitchButton.setVisibility(View.GONE);
                }

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
        if (mPreview != null && hasCamera()) {
            openCamera();
            mCaptureButton.setOnTouchListener(this);
            mCaptureButton.setOnClickListener(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Close the camera while we are not using so that other applications can use it.
        closeCamera();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save which camera is currently open to state.
        outState.putBoolean(STATE_IS_FRONT_CAMERA, mIsFrontCamera);
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
     * Check whether the device has a front-facing camera.
     *
     * @return True if the device has a front-facing camera; false otherwise.
     */
    private boolean hasFrontCamera() {
        final PackageManager packageManager = getPackageManager();
        return packageManager != null
                && packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }

    /**
     * Returns the camera ID (an integer between 0 and {@link android.hardware.Camera#getNumberOfCameras()})
     * for either the first front-facing or first back-facing camera.
     *
     * @param front True to find the first front-facing camera; false to find the first back-facing
     *              camera.
     *
     * @return The camera ID for the requested camera as an integer between between 0 and {@link
     * android.hardware.Camera#getNumberOfCameras()} or -1 if there was no matching camera.
     */
    private int findCameraId(boolean front) {
        final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        final int cameraCount = Camera.getNumberOfCameras();
        /*  Iterate through 0…getNumberOfCameras - 1 to find the camera ID of the first front or
        the first back camera. */
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if ((front && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                    || (!front && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK)) {
                return i;
            }
        }
        return NO_CAMERA;
    }

    /**
     * Returns a {@link android.hardware.Camera.CameraInfo} instance containing information on the
     * currently open camera.
     *
     * @return A {@link android.hardware.Camera.CameraInfo} instance containing information on the
     * currently open camera or `null` if no camera is open.
     */
    private Camera.CameraInfo getCameraInfo() {
        final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(mIsFrontCamera ? mFrontCameraId : mBackCameraId, cameraInfo);
        return cameraInfo;
    }

    /**
     * Open the first back-facing camera and grab a {@link android.hardware.Camera} instance.
     */
    private void openCamera() {
        if (mCamera != null) {
            if (mPreview.isStarted()) {
                mPreview.stop();
            }
            mCamera.cancelAutoFocus();
            mCamera.release();
        }
        mCamera = Camera.open(mIsFrontCamera ? mFrontCameraId : mBackCameraId);
        final Camera.CameraInfo cameraInfo = getCameraInfo();
        mPreview.setCamera(mCamera, cameraInfo);
        mPreview.start();
        final Camera.Parameters parameters = mCamera.getParameters();
        if (parameters.getMaxNumFocusAreas() > 0) {
            mPreview.startFocusAreaSelection();
            mPreview.setListener(this);
        } else {
            mPreview.stopFocusAreaSelection();
            mPreview.setListener(null);
        }
    }

    /**
     * Close the camera and release the previously obtained {@link android.hardware.Camera} instance
     * to make sure that other applications can grab the camera if needed.
     */
    private void closeCamera() {
        if (mCamera != null) {
            mPreview.setCamera(null, null);
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * Toggles the camera if we have two cameras available and starts the preview for the new
     * camera.
     */
    private void switchCamera() {
        if (mFrontCameraId != NO_CAMERA) {
            mIsFrontCamera = !mIsFrontCamera;
            openCamera();
        }
    }


    //
    // Auto-focus
    //

    private final Camera.AutoFocusCallback mAutoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
        }
    };


    //
    // Picture taking
    //

    /**
     * Callback for retrieving JPEG data when a picture is taken.
     */
    private final Camera.PictureCallback mJpegCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            showPicture(CameraUtils.bitmapFromRawBytes(
                    data,
                    mPictureView.getWidth(),
                    mPictureView.getHeight()
            ));
        }
    };

    /**
     * Checks if the camera is open and takes a picture, retrieving JPEG data.
     */
    private void takePicture() {
        if (mCamera != null) {
            // Take picture and capture raw image data.
            mCamera.takePicture(null, null, mJpegCallback);
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
        mPreview.setVisibility(View.INVISIBLE);
        mCaptureButton.setVisibility(View.GONE);
        mHandler.postDelayed(mHidePictureRunnable, mHidePictureDelay);
    }

    /**
     * Hides any shown picture and shows the preview and capture button.
     */
    private void hidePictureTaken() {
        mPreview.start();
        mPictureView.setVisibility(View.INVISIBLE);
        mPreview.setVisibility(View.VISIBLE);
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
        mPreview.setVisibility(View.GONE);
    }

    /**
     * Hides the error message text and show the camera preview.
     */
    private void hideError() {
        mErrorTextView.setVisibility(View.GONE);
        mPreview.setVisibility(View.VISIBLE);
    }


    //
    // View.OnClickListener implementation
    //

    @Override
    public void onClick(View v) {
        if (v == mSwitchButton) {
            switchCamera();
        } else if (v == mCaptureButton) {
            takePicture();
        }
    }


    //
    // View.OnTouchListener implementation
    //

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v == mCaptureButton) {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    mCamera.autoFocus(mAutoFocusCallback);
                    return false;
            }
        }
        return false;
    }


    //
    // PreviewListener implementation
    //

    @Override
    public void onAutoFocus() {
        mCamera.autoFocus(mAutoFocusCallback);
    }

    @Override
    public void onFocusAreaChange(Rect area) {
        if (mCamera != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            final Camera.Parameters parameters = mCamera.getParameters();
            ArrayList<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
            focusAreas.add(new Camera.Area(area, 10));
            parameters.setFocusAreas(focusAreas);
            mCamera.setParameters(parameters);
            mCamera.autoFocus(mAutoFocusCallback);
        }
    }

    @Override
    public void onMeteringAreaChange(Rect area) {
        if (mCamera != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            final Camera.Parameters parameters = mCamera.getParameters();
            ArrayList<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
            meteringAreas.add(new Camera.Area(area, 10));
            parameters.setMeteringAreas(meteringAreas);
            mCamera.setParameters(parameters);
            mCamera.autoFocus(mAutoFocusCallback);
        }
    }
}
