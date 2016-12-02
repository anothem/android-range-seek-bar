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
import android.os.Bundle;
import android.widget.FrameLayout;

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
        RangeSeekBar rangeSeekBar = new RangeSeekBar(this);
        // Set the range
        rangeSeekBar.setRangeValues(15, 90);
        rangeSeekBar.setSelectedMinValue(20);
        rangeSeekBar.setSelectedMaxValue(88);
        rangeSeekBar.setTextFormatter(new RangeSeekBar.TextFormatter() {
            @Override
            public String formatValue(int value) {
                return value + " kittens";
            }
        });

        // Add to layout
        FrameLayout layout = (FrameLayout) findViewById(R.id.seekbar_placeholder);
        layout.addView(rangeSeekBar);

        // Seek bar for which we will set text color in code
        RangeSeekBar rangeSeekBarTextColorWithCode = (RangeSeekBar) findViewById(R.id.rangeSeekBarTextColorWithCode);
        rangeSeekBarTextColorWithCode.setTextAboveThumbsColorResource(android.R.color.holo_blue_bright);

        // Seekbar with double values
        RangeSeekBar rsbDoubles = (RangeSeekBar) findViewById(R.id.rsb_double_values);
        rsbDoubles.setRangeValues(1523, 14835);
        rsbDoubles.setTextFormatter(new RangeSeekBar.TextFormatter() {
            @Override
            public String formatValue(int value) {
                return "£" + value / 100d;
            }
        });
    }
}
