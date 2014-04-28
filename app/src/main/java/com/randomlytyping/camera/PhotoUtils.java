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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Created by Huyen Tue Dao on 4/27/14.
 */
public class PhotoUtils {
    public static Bitmap bitmapFromRawBytes(byte[] data, int width, int height) {
        /*  Decode the dimensions of the bitmap data so we can determine a sample size that fits the
        requested width and height. */
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        final int actualWidth = options.outWidth;
        final int actualHeight = options.outHeight;

        // Calculate the sample size as power of 2 that will satisfy the requested width and height.
        int inSampleSize = 1;
        if ( actualWidth > width || actualHeight > height) {
            int shift = 0;
            while ((actualWidth >> shift + 1) > width && (actualHeight >> shift + 1) > height) {
                shift++;
            }
            if ( shift > 0 ) {
                inSampleSize <<= shift;
            }
        }

        /*  Reset BitmapFactory options to actually decode the byte array at the calculated sample
        size. */
        options.inSampleSize = inSampleSize;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }
}
