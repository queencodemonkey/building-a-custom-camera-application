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
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.io.IOException;
import java.util.List;

/**
 * Created by Huyen Tue Dao on 5/11/14.
 */
public class Preview extends ViewGroup implements SurfaceHolder.Callback, Camera.FaceDetectionListener {
    /**
     * Class tag for logging.
     */
    @SuppressWarnings("unused")
    private static final String TAG = "Preview";

    /**
     * Delay from last face detected update before it is determined there is "no face detected".
     */
    private static final long NO_FACE_DETECTED_DELAY = 80;


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
     * associated with the surface at this point.
     */
    public static final int PREVIEW_STATE_NO_SURFACE = 0;

    /**
     * Indicates that the surface has been created but that no camera has been attached to the
     * surface yet. So the surface is ready to display camera preview.
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
    // Metering and focus area constants
    //

    /**
     * Focus area minimum width in DP.
     * <p/>
     * Using Android-recommended <a href="http://developer.android.com/design/style/metrics-grids.html#48dp-rhythm">touch
     * target size.</a>
     */
    public static final int CAMERA_AREA_WIDTH_MINIMUM_DP = 48;

    /**
     * Focus area minimum height in DP.
     * <p/>
     * Using Android-recommended <a href="http://developer.android.com/design/style/metrics-grids.html#48dp-rhythm">touch
     * target size.</a>
     */
    public static final int CAMERA_AREA_HEIGHT_MINIMUM_DP = 48;


    // Views
    private SurfaceView mSurfaceView;
    private PreviewOverlay mOverlay;

    // Camera
    private Camera mCamera;
    private Camera.CameraInfo mCameraInfo;
    private Camera.Size mPreviewSize;

    // Preview state
    private int mState;

    // Overlay
    private Rect mOverlayBounds;

    // Metering area
    private boolean mMeteringAreaActive;

    // Focus area
    private boolean mFocusAreaActive;
    private int mFocusAreaWidth;
    private int mFocusAreaHeight;

    // Face detection
    private Handler mFaceDetectionHandler;
    private Runnable mFaceDetectionRunnable = new Runnable() {
        @Override
        public void run() {
            mOverlay.setFaces(null);
        }
    };

    // Listener
    private PreviewListener mListener;

    // References
    private Display mDisplay;
    private int mDisplayOrientation;


    //
    // Constructors/Initialization
    //

    /**
     * Constructor.
     *
     * @param context The current context.
     */
    public Preview(Context context) {
        super(context);
        init(context);
    }

    /**
     * Constructor.
     *
     * @param context The current context.
     * @param attrs   The attributes of the XML tag that is inflating the view.
     */
    public Preview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * Initialization helper for constructors.
     *
     * @param context The current context.
     */
    private void init(Context context) {
        // Create and add the SurfaceView and overlay.
        addView(mSurfaceView = new SurfaceView(context));
        addView(mOverlay = new PreviewOverlay(context));

        // Set up the SurfaceView for the preview.
        final SurfaceHolder holder = mSurfaceView.getHolder();

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

        // Initialize the camera preview state.
        mState = PREVIEW_STATE_NO_SURFACE;

        // Initialize the bounds.
        mOverlayBounds = new Rect();

        // Initialize metering and focus area size.
        final float density = getResources().getDisplayMetrics().density;
        mFocusAreaWidth = Math.round(CAMERA_AREA_WIDTH_MINIMUM_DP * density);
        mFocusAreaHeight = Math.round(CAMERA_AREA_HEIGHT_MINIMUM_DP * density);

        // Get a reference to the default display (generally, the device screen).
        final WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mDisplay = windowManager.getDefaultDisplay();
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
     * @param camera     The {@link android.hardware.Camera} that will utilize the PreviewView to
     *                   display its preview.
     * @param cameraInfo A {@link android.hardware.Camera.CameraInfo} containing information on the
     *                   passed {@code camera}.
     */
    public void setCamera(Camera camera, Camera.CameraInfo cameraInfo) {
        // Only set the camera if we only have one of the two to maintain consistency.
        if (camera == null ^ cameraInfo == null) {
            return;
        }
        mCamera = camera;
        mCameraInfo = cameraInfo;

        // Rotate preview to adjust for device rotation.
        if (mCamera != null) {
            // Counter-clockwise rotation of screen in degrees.
            final int degrees;
            switch (mDisplay.getRotation()) {
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
                case Surface.ROTATION_0:
                default:
                    degrees = 0;
                    break;
            }

            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mDisplayOrientation = (cameraInfo.orientation + degrees) % 360;
                // Compensation for mirroring of front cameras.
                mDisplayOrientation = (360 - mDisplayOrientation) % 360;
            } else {  // back-facing
                mDisplayOrientation = (cameraInfo.orientation - degrees + 360) % 360;
            }
            mCamera.setDisplayOrientation(mDisplayOrientation);
            if (mState > PREVIEW_STATE_READY) {
                updatePreviewSize(getWidth(), getHeight());
            }
            mCamera.setFaceDetectionListener(this);
        }
        requestLayout();
    }

    /**
     * Register a set of callbacks to be invoked when the user interacts with the viewfinder.
     *
     * @param listener The set of callbacks to be invoked when the user interacts with the
     *                 viewfinder.
     */
    public void setListener(PreviewListener listener) {
        this.mListener = listener;
    }

    //
    // Camera preview start/stop and setup.
    //

    public void start() {
        if (mCamera != null && (mState >= PREVIEW_STATE_READY)) {
            /*
                Try setting up the surface to be the passed Camera's preview display. If for
            some reason it errors, then change the state of the surface to indicate this.
             */
            try {
                mCamera.setPreviewDisplay(mSurfaceView.getHolder());
            } catch (IOException e) {
                mState = PREVIEW_STATE_ERROR;
                return;
            }

            // Actually start the camera preview and update the state variable.
            mCamera.startPreview();
            mState = PREVIEW_STATE_STARTED;
        }
    }

    public void stop() {
        if (mCamera != null) {
            // If there is a valid camera then stop its preview and update the state variable.
            mCamera.stopPreview();
            mState = PREVIEW_STATE_STOPPED;
        }
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
        if (previewSize != null) {
            parameters.setPreviewSize(previewSize.width, previewSize.height);
            mCamera.setParameters(parameters);
        }
        mPreviewSize = previewSize;
    }

    private Camera.Size findBestMatchingPreview(List<Camera.Size> previewSizes,
                                                float surfaceWidth, float surfaceHeight,
                                                boolean landscape) {
        if (!landscape) {
            final float swap = surfaceWidth;
            surfaceWidth = surfaceHeight;
            surfaceHeight = swap;
        }
        final float surfaceAspectRatio = surfaceWidth / surfaceHeight;
        float delta = 0.01f;
        while (delta < 1f) {
            for (Camera.Size size : previewSizes) {
                final float previewAspectRatio = (float) size.width / (float) size.height;
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
    // Metering and focus areas
    //

    public void startFocusAreaSelection() {
        mFocusAreaActive = true;
        mMeteringAreaActive = false;
    }

    public void stopFocusAreaSelection() {
        mFocusAreaActive = false;
    }

    public void startMeteringAreaSelection() {
        mMeteringAreaActive = true;
        mFocusAreaActive = false;
    }

    public void stopMeteringAreaSelection() {
        mMeteringAreaActive = false;
    }

    public void startFaceDetection() {
        if (mFaceDetectionHandler == null) {
            mFaceDetectionHandler = new Handler(Looper.getMainLooper());
        }
        mOverlay.setShowFaceBounds(true);
        invalidate();
    }

    public void stopFaceDetection() {
        mOverlay.setShowFaceBounds(false);
        mOverlay.setFaces(null);
        invalidate();
    }

    private Rect getAreaAt(float x, float y, int areaWidth, int areaHeight) {
        if (mOverlayBounds.width() == 0 || mOverlayBounds.height() == 0) {
            throw new IllegalArgumentException("Trying to create camera area from 0-dimensioned preview area.");
        }

        float[] coordinates = {x, y};
        CameraUtils.getSensorCoordinates(
                coordinates, mOverlayBounds,
                mDisplayOrientation, mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);

        final float halfAreaWidth = areaWidth * 0.5f;
        final float halfAreaHeight = areaHeight * 0.5f;
        return new Rect(
                Math.max(Math.round(coordinates[0] - halfAreaWidth), -1000),
                Math.max(Math.round(coordinates[1] - halfAreaHeight), -1000),
                Math.min(Math.round(coordinates[0] + halfAreaWidth), 1000),
                Math.min(Math.round(coordinates[1] + halfAreaHeight), 1000));
    }


    //
    // ViewGroup overrides
    //

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final float x = ev.getX();
        final float y = ev.getY();
        /*  If one of the area selection modes is active, the listener is set, and the touch point is
        within range of the preview surface, then change the area for the active mode. */
        if (mListener != null) {
            if ((mFocusAreaActive || mMeteringAreaActive)
                    && mOverlayBounds.contains(Math.round(x), Math.round(y))) {
                final Rect area = getAreaAt(x, y, mFocusAreaWidth, mFocusAreaHeight);
                if (mFocusAreaActive) {
                    mListener.onFocusAreaChange(area);
                } else {
                    mListener.onMeteringAreaChange(area);
                }
            } else {
                mListener.onAutoFocus();
            }
            return true;
        }
        return false;
    }


    //
    // View overrides
    //

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        updatePreviewSize(width, height);

        if (mPreviewSize != null) {
            int previewWidth = mPreviewSize.width;
            int previewHeight = mPreviewSize.height;
            if (width < height) {
                final int swap = previewWidth;
                previewWidth = previewHeight;
                previewHeight = swap;
            }

            final float previewAspectRatio = (float) previewWidth / (float) previewHeight;
            previewWidth = Math.round(height * previewAspectRatio);
            if (previewWidth > width) {
                previewWidth = width;
                previewHeight = width * previewHeight / previewWidth;
            } else {
                previewHeight = height;
            }

            final int exactWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                    previewWidth,
                    MeasureSpec.EXACTLY
            );
            final int exactHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                    previewHeight,
                    MeasureSpec.EXACTLY);

            mSurfaceView.measure(exactWidthMeasureSpec, exactHeightMeasureSpec);
            mOverlay.measure(exactWidthMeasureSpec, exactHeightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            int viewWidth = mSurfaceView.getMeasuredWidth();
            int viewHeight = mSurfaceView.getMeasuredHeight();
            left = Math.round((right - left - viewWidth) * 0.5f);
            top = Math.round((bottom - top - viewHeight) * 0.5f);
            right = left + viewWidth;
            bottom = top + viewHeight;
            mSurfaceView.layout(left, top, right, bottom);
            mOverlayBounds.left = left;
            mOverlayBounds.top = top;
            mOverlayBounds.right = right;
            mOverlayBounds.bottom = bottom;
            mOverlay.layout(left, top, right, bottom);
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
    // FaceDetectionListener implementation
    //

    @Override
    public void onFaceDetection(Camera.Face[] faces, Camera camera) {
        if (faces.length > 0) {

            // Remove callbacks that check whether no more faces are being detected.
            mFaceDetectionHandler.removeCallbacks(mFaceDetectionRunnable);

            final boolean frontFacing = mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;
            for (Camera.Face face : faces) {
                float[] faceCoordinates = {face.rect.left, face.rect.top, face.rect.right, face.rect.bottom};
                CameraUtils.getViewCoordinates(faceCoordinates, mDisplayOrientation, frontFacing,
                        mOverlayBounds);
                final float x1, x2, y1, y2;
                if (faceCoordinates[0] < faceCoordinates[2]) {
                    x1 = faceCoordinates[0];
                    x2 = faceCoordinates[2];
                } else {
                    x1 = faceCoordinates[2];
                    x2 = faceCoordinates[0];
                }
                if (faceCoordinates[1] < faceCoordinates[3]) {
                    y1 = faceCoordinates[1];
                    y2 = faceCoordinates[3];
                } else {
                    y1 = faceCoordinates[3];
                    y2 = faceCoordinates[1];
                }
                face.rect.left = Math.round(x1);
                face.rect.top = Math.round(y1);
                face.rect.right = Math.round(x2);
                face.rect.bottom = Math.round(y2);

                /*  If the left eye coordinate exists, then all facial features are supported and
                they all exist. */
                if (face.leftEye != null) {
                    CameraUtils.getViewCoordinates(face.leftEye, mDisplayOrientation, frontFacing,
                            mOverlayBounds);
                    CameraUtils.getViewCoordinates(face.rightEye, mDisplayOrientation, frontFacing,
                            mOverlayBounds);
                    CameraUtils.getViewCoordinates(face.mouth, mDisplayOrientation, frontFacing,
                            mOverlayBounds);
                }
            }
            mOverlay.setFaces(faces);
            mFaceDetectionHandler.postDelayed(mFaceDetectionRunnable, NO_FACE_DETECTED_DELAY);
        }
    }


    //
    // Interface definitions
    //

    /**
     * Interface definition for a set of callbacks to be invoked when the user interacts with the
     * preview and activates various features.
     */
    public interface PreviewListener {

        /**
         * Called when the user chooses to use pure auto focus.
         */
        void onAutoFocus();

        /**
         * Called when the user selects a new focus area.
         *
         * @param area The bounds of the focus area.
         */
        void onFocusAreaChange(Rect area);

        /**
         * Called when the user selects a new metering area.
         *
         * @param area The bounds of the metering area.
         */
        void onMeteringAreaChange(Rect area);
    }
}