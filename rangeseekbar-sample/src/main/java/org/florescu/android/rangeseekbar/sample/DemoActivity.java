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

package org.florescu.android.rangeseekbar.sample;

import android.app.Activity;
import android.graphics.Path;
import android.os.Bundle;
import android.widget.LinearLayout;

import org.florescu.android.rangeseekbar.RangeSeekBar;

public class DemoActivity extends Activity {

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Setup the new range seek bar
        RangeSeekBar<Integer> rangeSeekBar = new RangeSeekBar<Integer>(this);
        // Set the range
        rangeSeekBar.setRangeValues(15, 90);
        rangeSeekBar.setSelectedMinValue(20);
        rangeSeekBar.setSelectedMaxValue(88);

        // Add to layout
        LinearLayout layout = (LinearLayout) findViewById(R.id.seekbar_placeholder);
        layout.addView(rangeSeekBar);

        // Find the seek bar for which we will add a custom shadow
        RangeSeekBar<Integer> seekBar = (RangeSeekBar<Integer>) findViewById(R.id.shadow_path_seek_bar);
        int height = getResources().getDimensionPixelSize(R.dimen.rect_height);
        int width = getResources().getDimensionPixelSize(R.dimen.rect_width);
        Path shadowPath = new Path();
        shadowPath.addRect(-(float) width / 2f, -(float) height / 2f, (float) width / 2f, (float) height / 2f, Path.Direction.CW);
        seekBar.setThumbShadowPath(shadowPath);

        // Seek bar for which we will set text color in code
        RangeSeekBar rangeSeekBarTextColorWithCode = (RangeSeekBar) findViewById(R.id.rangeSeekBarTextColorWithCode);
        rangeSeekBarTextColorWithCode.setTextAboveThumbsColorResource(android.R.color.holo_blue_bright);
    }
}
