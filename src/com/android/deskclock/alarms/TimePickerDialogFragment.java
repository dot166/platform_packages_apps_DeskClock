/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.deskclock.alarms;

import static com.google.android.material.timepicker.MaterialTimePicker.INPUT_MODE_CLOCK;
import static com.google.android.material.timepicker.TimeFormat.CLOCK_12H;
import static com.google.android.material.timepicker.TimeFormat.CLOCK_24H;

import android.text.format.DateFormat;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.timepicker.MaterialTimePicker;

import java.util.Calendar;

/**
 * DialogFragment used to show TimePicker.
 */
public class TimePickerDialogFragment {

    /**
     * Tag for timer picker fragment in FragmentManager.
     */
    private static final String TAG = "TimePickerDialogFragment";

    public static void show(Fragment fragment) {
        final Calendar now = Calendar.getInstance();
        TimePickerDialogFragment.show(fragment, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    public static void show(Fragment parentFragment, int hourOfDay, int minute) {
        if (!(parentFragment instanceof OnTimeSetListener)) {
            throw new IllegalArgumentException("Fragment must implement OnTimeSetListener");
        }

        final FragmentManager manager = parentFragment.getChildFragmentManager();
        if (manager.isDestroyed()) {
            return;
        }

        // Make sure the dialog isn't already added.
        removeTimeEditDialog(manager);

        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setHour(hourOfDay)
                .setMinute(minute)
                .setTimeFormat(DateFormat.is24HourFormat(parentFragment.getContext()) ? CLOCK_24H : CLOCK_12H)
                .setInputMode(INPUT_MODE_CLOCK)
                .build();

        timePicker.addOnPositiveButtonClickListener(v -> ((OnTimeSetListener) parentFragment).onTimeSet(timePicker.getHour(), timePicker.getMinute()));
        timePicker.show(manager, TAG);
    }

    public static void removeTimeEditDialog(FragmentManager manager) {
        if (manager != null) {
            final Fragment prev = manager.findFragmentByTag(TAG);
            if (prev != null) {
                manager.beginTransaction().remove(prev).commit();
            }
        }
    }

    /**
     * The callback interface used to indicate the user is done filling in the time (e.g. they
     * clicked on the 'OK' button).
     */
    public interface OnTimeSetListener {
        /**
         * Called when the user is done setting a new time and the dialog has closed.
         * @param hourOfDay the hour that was set
         * @param minute    the minute that was set
         */
        void onTimeSet(int hourOfDay, int minute);
    }
}
