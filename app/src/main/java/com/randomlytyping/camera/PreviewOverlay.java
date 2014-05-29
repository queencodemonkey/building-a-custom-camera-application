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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by Huyen Tue Dao on 5/27/14.
 */
public class PreviewOverlay extends View {

    // Thirds grid
    private boolean mShowGrid;
    private int mGridLineWidth;
    private int mGridBorderOffset;
    private int mFirstX;
    private int mFirstY;
    private int mSecondX;
    private int mSecondY;
    private int mThirdX;
    private int mThirdY;
    private Paint mGridLinePaint;

    // Faces
    private boolean mShowFaceBounds;
    private boolean mShowFaceScore;
    private Camera.Face[] mFaces;
    private int mFaceBorderWidth;
    private int mFaceTextOffsetX;
    private int mFaceTextOffsetY;
    private TextPaint mFacePaint;


    //
    // Constructors/Initialization
    //
    
    /**
     * Constructor.
     *
     * @param context The current context.
     */
    public PreviewOverlay(Context context) {
        super(context);
        init(context);
    }

    /**
     * Constructor.
     *
     * @param context The current context.
     * @param attrs   The attributes of the XML tag that is inflating the view.
     */
    public PreviewOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * Initialization helper for constructors.
     *
     * @param context The current context.
     */
    private void init(Context context) {
        mGridLineWidth = 1;
        mFaceBorderWidth = 1;

        final Resources resources = context.getResources();
        final float density = resources.getDisplayMetrics().density;
        // Set up grid line paint.
        final int gridLineWidth = Math.round(density * mGridLineWidth);
        mGridLinePaint = new Paint();
        mGridLinePaint.setColor(Color.LTGRAY);
        mGridLinePaint.setStrokeWidth(gridLineWidth);
        mGridBorderOffset = (int) Math.ceil(gridLineWidth * 0.5);

        // Set up face border paint.
        final int paintBorderWidth = Math.round(density * mFaceBorderWidth);
        mFacePaint = new TextPaint();
        mFacePaint.setColor(Color.RED);
        mFacePaint.setStyle(Paint.Style.STROKE);
        mFacePaint.setStrokeWidth(paintBorderWidth);
        mFacePaint.setTextSize(20);
        mFaceTextOffsetX = Math.round(Math.abs(mFacePaint.getFontMetrics().ascent));
        mFaceTextOffsetY = 2 * mFaceTextOffsetX;

        // Show face score by default.
        mShowFaceScore = true;
    }

    /**
     * Whether to show the "Rule of Thirds" grid.
     *
     * @param showGrid True to immediately show the "Rule of Thirds" grid; false to immediately hide
     *                 it.
     */
    public void setShowGrid(boolean showGrid) {
        mShowGrid = showGrid;
        invalidate();
    }

    public void setShowFaceBounds(boolean showFaceBounds) {
        mShowFaceBounds = showFaceBounds;
        invalidate();
    }

    public void setShowFaceScore(boolean showFaceScore) {
        mShowFaceScore = showFaceScore;
        invalidate();
    }

    public void setFaces(Camera.Face[] faces) {
        mFaces = faces;
        invalidate();
    }

    //
    // View overrides
    //

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed && mShowGrid) {
            final int width = right - left;
            final int height = bottom - top;

            mFirstX = left + Math.round(width / 3f);
            mFirstY = top + Math.round(height / 3f);
            mSecondX = left + Math.round(width * 2f / 3f);
            mSecondY = left + Math.round(height * 2f / 3f);
            mThirdX = right - mGridBorderOffset;
            mThirdY = bottom - mGridBorderOffset;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw the thirds grid if enabled.
        if (mShowGrid) {
            final int width = canvas.getWidth();
            canvas.drawLine(mGridBorderOffset, mGridBorderOffset, width, mGridBorderOffset, mGridLinePaint);
            canvas.drawLine(mGridBorderOffset, mFirstY, width, mFirstY, mGridLinePaint);
            canvas.drawLine(mGridBorderOffset, mSecondY, width, mSecondY, mGridLinePaint);
            canvas.drawLine(mGridBorderOffset, mThirdY, width, mThirdY, mGridLinePaint);
            final int height = canvas.getHeight();
            canvas.drawLine(mGridBorderOffset, mGridBorderOffset, mGridBorderOffset, height, mGridLinePaint);
            canvas.drawLine(mFirstX, mGridBorderOffset, mFirstX, height, mGridLinePaint);
            canvas.drawLine(mSecondX, mGridBorderOffset, mSecondX, height, mGridLinePaint);
            canvas.drawLine(mThirdX, mGridBorderOffset, mThirdX, height, mGridLinePaint);
        }
        if (mShowFaceBounds && mFaces != null) {
            for (Camera.Face face: mFaces) {
                final Rect faceBounds = face.rect;
                final int x = faceBounds.left;
                final int y = faceBounds.top;
                if (mShowFaceScore) {
                    canvas.drawRect(x, y, faceBounds.right, faceBounds.bottom, mFacePaint);
                    canvas.drawText(
                            String.format("%d", face.score),
                            x + mFaceTextOffsetX, y + mFaceTextOffsetY,
                            mFacePaint
                    );
                }
            }
        }
    }
}
