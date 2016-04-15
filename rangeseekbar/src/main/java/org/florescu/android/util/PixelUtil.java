/*
Copyright 2015 Alex Florescu
Copyright 2014 Yahoo Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.florescu.android.util;

import android.content.Context;

/**
 * Util class for converting between dp, px and other magical pixel units
 */
public class PixelUtil {

    private PixelUtil() {
    }

    public static int dpToPx(Context context, int dp) {
        return Math.round(dp * getDensity(context));
    }

    public static int pxToDp(Context context, int px) {
        return Math.round(px / getDensity(context));
    }

    public static int spToPx(Context context, float sp) {
        return Math.round(sp * getScaledDensity(context));
    }

    public static int pxToSp(Context context, float px) {
        return Math.round(px / getScaledDensity(context));
    }

    private static float getDensity(Context context) {
        return context.getResources().getDisplayMetrics().density;
    }

    private static float getScaledDensity(Context context) {
        return context.getResources().getDisplayMetrics().scaledDensity;
    }

}
