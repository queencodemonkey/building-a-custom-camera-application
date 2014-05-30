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

import android.content.Context;
import android.hardware.Camera;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.IOException;
import java.util.List;

/**
 * Created by Huyen Tue Dao on 5/6/14.
 */
public class PreviewSurface extends SurfaceView implements SurfaceHolder.Callback {

    /**
     * Class tag for logging.
     */
    @SuppressWarnings("unused")
    private static final String TAG = "PreviewSurface";


    //
    // Camera preview state constants
    //

    /**
     * Indicates that there was an error starting the camera preview.
     */
    public static final int PREVIEW_STATE_ERROR = -1;

    /**
     * Indicates that there surface has not yet been created or has been destroyed and so the camera
     * preview cannot be started or stopped yet. There may be a {@link android.hardware.Camera}
     * associated with the PreviewSurface at this point.
     */
    public static final int PREVIEW_STATE_NO_SURFACE = 0;

    /**
     * Indicates that the surface has been created but that no camera has been attached to the
     * PreviewSurface yet. So the PreviewSurface is ready to   display camera preview.
     */
    public static final int PREVIEW_STATE_READY = 1;

    /**
     * Indicates that the camera preview started and is running.
     */
    public static final int PREVIEW_STATE_STARTED = 100;

    /**
     * Indicates that the camera preview is stopped.
     * <p/>
     * This state occurs when the camera view is explicitly stopped.
     */
    public static final int PREVIEW_STATE_STOPPED = 200;


    //
    // Fields
    //

    // Camera
    private Camera mCamera;
    private Camera.CameraInfo mCameraInfo;
    private Camera.Size mPreviewSize;

    // Preview state
    private int mState;

    // Listeners/Callbacks
    private PreviewStateChangeListener mPreviewStateChangeListener;

    // References
    private Display mDefaultDisplay;

    //
    // Constructors/Initialization
    //

    /**
     * Constructor.
     *
     * @param context The current context.
     */
    public PreviewSurface(Context context) {
        super(context);
        init(context);
    }

    /**
     * Constructor.
     *
     * @param context The current context.
     * @param attrs   The attributes of the XML tag that is inflating the view.
     */
    public PreviewSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * Initialization helper for constructors.
     */
    private void init(Context context) {
        final SurfaceHolder holder = getHolder();

        /*
            Add the PreviewView as a callback to its SurfaceHolder so we can start and stop the
        associated camera's preview when the PreviewView's surface is created, changed, or
        destroyed.
         */
        holder.addCallback(this);

        /*
            In order to support Gingerbread and below, need to call SurfaceHolder#setType.
        If Gingerbread support is not needed, then do not call #setType as it is deprecated
        and higher API levels take care of this setting automatically.
        */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        // Initializes the camera preview state.
        mState = PREVIEW_STATE_NO_SURFACE;

        final WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mDefaultDisplay = windowManager.getDefaultDisplay();
    }


    //
    // Getters/Setters
    //

    public boolean isStarted() {
        return mState == PREVIEW_STATE_STARTED;
    }

    public boolean isStopped() {
        return mState == PREVIEW_STATE_STOPPED;
    }

    /**
     * Sets the {@link android.hardware.Camera} instance that will utilize the PreviewView to
     * display its preview.
     *
     * @param camera            The {@link android.hardware.Camera} that will utilize the
     *                          PreviewView to display its preview.
     * @param cameraInfo        A {@link android.hardware.Camera.CameraInfo} containing information
     *                          on the passed {@code camera}.
     */
    public void setCamera(Camera camera, Camera.CameraInfo cameraInfo) {
        // Only set the camera if we only have one of the two to maintain consistency.
        if (camera == null ^ cameraInfo == null) {
            return;
        }
        mCamera = camera;
        mCameraInfo = cameraInfo;
    }

    public void updatePreviewOrientation() {
        if (mDefaultDisplay == null) {
            return;
        }
        int degrees = 0;
        switch (mDefaultDisplay.getRotation()) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (mCameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (mCameraInfo.orientation - degrees + 360) % 360;
        }
        mCamera.setDisplayOrientation(result);
    }


    public void setPreviewStateChangeListener(PreviewStateChangeListener previewStateChangeListener) {
        mPreviewStateChangeListener = previewStateChangeListener;
    }

    //
    // Camera preview setup
    //

    /**
     * Resets the camera preview by removing the open camera and its information.
     */
    public void reset() {
        setCamera(null, null);
    }

    private void updatePreviewSize(int width, int height) {
        if (mCamera == null) {
            return;
        }

        final Camera.Parameters parameters = mCamera.getParameters();
        final List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        final boolean landscape = width > height;
        Camera.Size previewSize = findBestMatchingPreview(
                supportedPreviewSizes,
                width,
                height,
                landscape
        );
        if (previewSize == null) {
            previewSize = landscape
                    ? findLargestPreview(supportedPreviewSizes, width, height)
                    : findLargestPreview(supportedPreviewSizes, height, width);
        }
        mPreviewSize = previewSize;
            parameters.setPreviewSize(previewSize.width, previewSize.height);
        mCamera.setParameters(parameters);
    }

    private Camera.Size findBestMatchingPreview(List<Camera.Size> previewSizes,
                                                float surfaceWidth, float surfaceHeight,
                                                boolean landscape) {
        final float surfaceAspectRatio = landscape
                ? surfaceWidth / surfaceHeight
                : surfaceHeight / surfaceWidth;
        float delta = 0.01f;
        while (delta < 1f) {
            for (Camera.Size size : previewSizes) {
                final float previewAspectRatio =  (float) size.width / (float) size.height;
                final float difference = Math.abs(surfaceAspectRatio - previewAspectRatio);
                if (size.width < surfaceWidth && size.height < surfaceHeight && difference < 0.1) {
                    return size;
                }
            }
            delta *= 10f;
        }
        return null;
    }

    private Camera.Size findLargestPreview(List<Camera.Size> previewSizes,
                                           float surfaceWidth, float surfaceHeight) {
        for (Camera.Size size : previewSizes) {
            if (size.width < surfaceWidth && size.height < surfaceHeight) {
                return size;
            }
        }
        return null;
    }


    //
    // Camera preview control
    //

    public void start() {
        if (mCamera != null && (mState >= PREVIEW_STATE_READY)) {
            /*
                Try setting up the PreviewSurface to be the passed Camera's preview display. If for
            some reason it errors, then change the state of the PreviewSurface to indicate this.
             */
            try {
                mCamera.setPreviewDisplay(getHolder());
            } catch (IOException e) {
                mState = PREVIEW_STATE_ERROR;
                return;
            }

            // Actually start the camera preview and update the state variable.
            mCamera.startPreview();
            mState = PREVIEW_STATE_STARTED;

            /*  If there is a listener for the preview state change, then notify it that the preview
            started. */
            if (mPreviewStateChangeListener != null) {
                mPreviewStateChangeListener.onPreviewStart();
            }
        }
    }

    public void stop() {
        if (mCamera != null) {
            // If there is a valid camera then stop its preview and update the state variable.
            mCamera.stopPreview();
            mState = PREVIEW_STATE_STOPPED;


            /*  If there is a listener for the preview state change, then notify it that the preview
            started. */
            if (mPreviewStateChangeListener != null) {
                mPreviewStateChangeListener.onPreviewStop();
            }
        }
    }


    //
    // SurfaceHolder.Callback implementation
    //

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mState = PREVIEW_STATE_READY;
        start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mCamera != null) {
            mState = PREVIEW_STATE_STOPPED;
            mCamera.stopPreview();

            updatePreviewOrientation();
            updatePreviewSize(getMeasuredWidth(), getMeasuredHeight());

            mCamera.startPreview();
            mState = PREVIEW_STATE_STARTED;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // If the surface is destroyed, stop the camera preview.
        if (mCamera != null) {
            mCamera.stopPreview();
        }

        mState = PREVIEW_STATE_NO_SURFACE;
    }


    //
    // Interface definitions
    //

    /**
     * Interface definition for a set of callbacks to be invoked when the state of the camera
     * preview changes.
     */
    public interface PreviewStateChangeListener {

        /**
         * Called when the camera preview starts.
         */
        void onPreviewStart();

        /**
         * Called when the camera preview stops.
         */
        void onPreviewStop();
    }
}
