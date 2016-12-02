package org.florescu.android.rangeseekbar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class RangeSeekBarTest {

    @Test
    public void emptyTest() {
        // TODO
        assertThat(true).isTrue();
    }
}