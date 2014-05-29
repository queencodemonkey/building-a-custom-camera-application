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

/**
 * Created by Huyen Tue Dao on 5/7/14.
 */
public enum Rotation {

    ZERO(0), NINETY(90), ONE_EIGHTY(180), TWO_SEVENTY(270);

    private static final int DELTA = 5;

    private final int degrees;
    private final int lowerBound;
    private int upperBound;

    private Rotation(int degrees) {
        this.degrees = degrees;
        this.lowerBound = (degrees - DELTA + 360) % 360;
        this.upperBound = (degrees + DELTA) % 360;
    }

    public int getDegrees() {
        return degrees;
    }

    public boolean test(int rotation) {
        rotation %= 360;
        return lowerBound < upperBound
                ? rotation >= lowerBound && rotation <= upperBound
                : rotation >= lowerBound || rotation <= upperBound;
    }

}