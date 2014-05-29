package com.randomlytyping.camera;/*
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

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Huyen Tue Dao on 5/7/14.
 */
public class BasicParametersActivity extends Activity
        implements Camera.ErrorCallback, Camera.OnZoomChangeListener, PreviewSurface.PreviewStateChangeListener, View.OnClickListener {
    /**
     * Class tag for logging.
     */

    @SuppressWarnings("unused")
    private static final String TAG = "BasicParametersActivity";

    /**
     * ID value for a particular camera (front or back) that was not found.
     */
    private static final int NO_CAMERA = -1;

    /**
     * Whether the currently open camera is the front-facing camera.
     */
    private static final String STATE_IS_FRONT_CAMERA = "isFrontCamera";

    // Views
    private PreviewSurface mPreview;
    private TextView mErrorTextView;

    private ImageView mPictureView;
    private ImageButton mCaptureButton;
    private ImageButton mSwitchButton;

    private ImageButton mFlashButton;
    private ImageButton mExposureButton;
    private ImageButton mColorEffectButton;
    private ImageButton mZoomButton;
    private ImageButton mWhiteBalanceButton;
    private ImageButton mSceneButton;

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

        setContentView(R.layout.activity_basic_parameters);

        // Grab references to the SurfaceView for the preview and the TextView for display errors.
        mPreview = (PreviewSurface) findViewById(R.id.preview);
        mErrorTextView = (TextView) findViewById(R.id.error_text);

        // Grab references to the picture-taking-related views.
        mPictureView = (ImageView) findViewById(R.id.picture_taken);
        mCaptureButton = (ImageButton) findViewById(R.id.capture_button);

        // Grab references to camera-switching-related views.
        mSwitchButton = (ImageButton) findViewById(R.id.switch_button);

        // Grab reference to other camera parameters.
        mFlashButton = (ImageButton) findViewById(R.id.flash_button);
        mExposureButton = (ImageButton) findViewById(R.id.exposure_button);
        mColorEffectButton = (ImageButton) findViewById(R.id.color_effect_button);
        mZoomButton = (ImageButton) findViewById(R.id.zoom_button);
        mWhiteBalanceButton = (ImageButton) findViewById(R.id.white_balance_button);
        mSceneButton = (ImageButton) findViewById(R.id.scene_button);

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
                mCaptureButton.setOnClickListener(this);

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

                // Set up other camera parameter controls.
                mFlashButton.setOnClickListener(this);
                mExposureButton.setOnClickListener(this);
                mColorEffectButton.setOnClickListener(this);

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

            // Setup the zoom button.
            final Camera.Parameters parameters = mCamera.getParameters();
            if (parameters.isZoomSupported()) {
                mZoomButton.setOnClickListener(this);
            } else {
                mZoomButton.setEnabled(false);
                mZoomButton.setVisibility(View.GONE);
            }

            final List<String> supportedWhiteBalance = parameters.getSupportedWhiteBalance();
            if (supportedWhiteBalance != null) {
                mWhiteBalanceButton.setOnClickListener(this);
            } else {
                mWhiteBalanceButton.setEnabled(false);
                mWhiteBalanceButton.setVisibility(View.GONE);
            }

            final List<String> supportedSceneModes = parameters.getSupportedSceneModes();
            if (supportedSceneModes != null) {
                mSceneButton.setOnClickListener(this);
            } else {
                mSceneButton.setEnabled(false);
                mSceneButton.setVisibility(View.GONE);
            }
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
            mCamera.release();
        }
        mCamera = Camera.open(mIsFrontCamera ? mFrontCameraId : mBackCameraId);
        mCamera.setZoomChangeListener(this);
        mPreview.setCamera(mCamera, getCameraInfo());
        mPreview.setPreviewStateChangeListener(this);
        mPreview.start();
    }

    /**
     * Close the camera and release the previously obtained {@link android.hardware.Camera} instance
     * to make sure that other applications can grab the camera if needed.
     */
    private void closeCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
            mPreview.reset();
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

    private Camera.AutoFocusCallback mAutoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
        }
    };

    /**
     * Toggles the flash mode.
     */
    private void toggleFlashMode() {
        if (mCamera != null) {
            final Camera.Parameters parameters = mCamera.getParameters();
            final String flashMode = parameters.getFlashMode();
            final List<String> supportedFlashModes = parameters.getSupportedFlashModes();
            parameters.setFlashMode(supportedFlashModes.get(
                    (supportedFlashModes.indexOf(flashMode) + 1) % supportedFlashModes.size()
            ));
            mCamera.setParameters(parameters);
        }
    }

    private void toggleExposureCompensation() {
        if (mCamera != null) {
            final Camera.Parameters parameters = mCamera.getParameters();
            final int minExposureCompensation = parameters.getMinExposureCompensation();
            final int maxExposureCompensation = parameters.getMaxExposureCompensation();
            int exposureCompensation = parameters.getExposureCompensation();
            exposureCompensation++;
            if (exposureCompensation > maxExposureCompensation) {
                exposureCompensation = minExposureCompensation;
            }
            parameters.setExposureCompensation(exposureCompensation);
            mCamera.setParameters(parameters);
        }
    }

    private void toggleColorEffect() {
        if (mCamera != null) {
            final Camera.Parameters parameters = mCamera.getParameters();
            final String colorEffect = parameters.getColorEffect();
            final List<String> supportedColorEffects = parameters.getSupportedColorEffects();
            parameters.setColorEffect(supportedColorEffects.get(
                    (supportedColorEffects.indexOf(colorEffect) + 1) % supportedColorEffects.size()
            ));
            mCamera.setParameters(parameters);
        }
    }

    private void toggleZoom() {
        if (mCamera != null) {
            final Camera.Parameters parameters = mCamera.getParameters();
            parameters.setZoom((parameters.getZoom() + 1) % parameters.getMaxZoom());
            mCamera.setParameters(parameters);
        }
    }

    private void toggleWhiteBalance() {
        if (mCamera != null) {
            final Camera.Parameters parameters = mCamera.getParameters();
            final String whiteBalance = parameters.getWhiteBalance();
            final List<String> supportedWhiteBalance = parameters.getSupportedWhiteBalance();
            parameters.setWhiteBalance(supportedWhiteBalance.get(
                    (supportedWhiteBalance.indexOf(whiteBalance) + 1) % supportedWhiteBalance.size()
            ));
            mCamera.setParameters(parameters);
        }
    }

    private void toggleScene() {
        if (mCamera != null) {
            final Camera.Parameters parameters = mCamera.getParameters();
            final String scene = parameters.getSceneMode();
            final List<String> supportedSceneModes = parameters.getSupportedSceneModes();
            parameters.setSceneMode(supportedSceneModes.get(
                    (supportedSceneModes.indexOf(scene) + 1) % supportedSceneModes.size()
            ));
            mCamera.setParameters(parameters);
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
    // Camera.ErrorCallback implementation
    //

    @Override
    public void onError(int error, Camera camera) {
        Log.e(TAG, String.format("onError: %d", error));
    }


    //
    // Camera.OnZoomChangeListener implementation
    //

    @Override
    public void onZoomChange(int zoomValue, boolean stopped, Camera camera) {
        Log.e(TAG, String.format("onError: %d", zoomValue));
    }

    //
    // PreviewSurface.PreviewStateChangeListener implementation
    //

    @Override
    public void onPreviewStart() {
    }

    @Override
    public void onPreviewStop() {

    }


    //
    // View.OnClickListener implementation
    //

    @Override
    public void onClick(View v) {
        if (v == mCaptureButton) {
            takePicture();
        } else if (v == mSwitchButton) {
            switchCamera();
        } else if (v == mFlashButton) {
            toggleFlashMode();
        } else if (v == mExposureButton) {
            toggleExposureCompensation();
        } else if (v == mColorEffectButton) {
            toggleColorEffect();
        } else if (v == mZoomButton) {
            toggleZoom();
        } else if (v == mWhiteBalanceButton) {
            toggleWhiteBalance();
        } else if (v == mSceneButton) {
            toggleScene();
        }
    }
}
