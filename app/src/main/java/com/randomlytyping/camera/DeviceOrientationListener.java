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
import android.view.OrientationEventListener;

/**
 * The CameraOrientationEventListener class is a {@link android.view.OrientationEventListener}
 * subclass that helps adjust the orientation of the camera preview when the orientation of the
 * device changes.
 * <p/>
 * Created by Huyen Tue Dao on 5/8/14.
 */
public class DeviceOrientationListener extends OrientationEventListener {

    /**
     * Constructor.
     *
     * @param context The current context.
     */
    public DeviceOrientationListener(Context context) {
        super(context);
    }


    //
    // OrientationEventListener overrides
    //

    @Override
    public void onOrientationChanged(int orientation) {
        if (Rotation.ZERO.test(orientation)) {
        } else if (Rotation.NINETY.test(orientation)) {
        } else if (Rotation.ONE_EIGHTY.test(orientation)) {
        } else if (Rotation.TWO_SEVENTY.test(orientation)) {
        }
    }
}