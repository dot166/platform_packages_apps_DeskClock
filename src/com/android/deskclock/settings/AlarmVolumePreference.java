/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock.settings;

import static android.content.Context.AUDIO_SERVICE;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.media.AudioManager.STREAM_ALARM;
import static android.view.View.GONE;

import android.app.NotificationManager;
import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceViewHolder;

import com.android.deskclock.R;
import com.android.deskclock.RingtonePreviewKlaxon;
import com.android.deskclock.data.DataModel;
import com.google.android.material.slider.Slider;

import io.github.dot166.jlib.preference.SliderPreference;

public class AlarmVolumePreference extends SliderPreference {

    private static final long ALARM_PREVIEW_DURATION_MS = 2000;

    private Slider mSeekbar;
    private boolean mPreviewPlaying;

    public AlarmVolumePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        getMValueTextView().setVisibility(GONE);
        setMValueTextView(null); // disable value view, by force if necessary
        getMResetImageButton().setVisibility(GONE);
        setMResetImageButton(null); // disable reset button, by force if necessary
        getMPlusImageButton().setVisibility(GONE);
        setMPlusImageButton(null); // disable plus button view, by force if necessary
        getMMinusImageButton().setVisibility(GONE);
        setMMinusImageButton(null); // disable minus button button, by force if necessary

        final Context context = getContext();
        final AudioManager audioManager = (AudioManager) context.getSystemService(AUDIO_SERVICE);

        // Disable click feedback for this preference.
        holder.itemView.setClickable(false);

        // Minimum volume for alarm is not 0, calculate it.
        int maxVolume = audioManager.getStreamMaxVolume(STREAM_ALARM) -
                audioManager.getStreamMinVolume(STREAM_ALARM);
        mSeekbar = getMSlider();
        mSeekbar.setValueTo(maxVolume);
        mSeekbar.setValue(audioManager.getStreamVolume(STREAM_ALARM) -
                audioManager.getStreamMinVolume(STREAM_ALARM));
        ((ImageView) holder.findViewById(android.R.id.icon))
                .setImageResource(R.drawable.ic_alarm_small);

        onSeekbarChanged();

        final ContentObserver volumeObserver = new ContentObserver(mSeekbar.getHandler()) {
            @Override
            public void onChange(boolean selfChange) {
                // Volume was changed elsewhere, update our slider.
                mSeekbar.setValue(audioManager.getStreamVolume(STREAM_ALARM) -
                        audioManager.getStreamMinVolume(STREAM_ALARM));
            }
        };

        mSeekbar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                context.getContentResolver().registerContentObserver(Settings.System.CONTENT_URI,
                        true, volumeObserver);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                context.getContentResolver().unregisterContentObserver(volumeObserver);
            }
        });

        mSeekbar.addOnChangeListener((seekBar, progress, fromUser) -> {
            if (fromUser) {
                float newVolume = progress + audioManager.getStreamMinVolume(STREAM_ALARM);
                audioManager.setStreamVolume(STREAM_ALARM, (int) newVolume, 0);
            }
            onSeekbarChanged();
        });


        mSeekbar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {

            @Override
            public void onStartTrackingTouch(Slider seekBar) {
            }

            @Override
            public void onStopTrackingTouch(Slider seekBar) {
                if (!mPreviewPlaying) {
                    // If we are not currently playing, start.
                    RingtonePreviewKlaxon.start(
                            context, DataModel.getDataModel().getDefaultAlarmRingtoneUri());
                    mPreviewPlaying = true;
                    seekBar.postDelayed(() -> {
                        RingtonePreviewKlaxon.stop(context);
                        mPreviewPlaying = false;
                    }, ALARM_PREVIEW_DURATION_MS);
                }
            }
        });
    }

    private void onSeekbarChanged() {
        mSeekbar.setEnabled(doesDoNotDisturbAllowAlarmPlayback());
    }

    private boolean doesDoNotDisturbAllowAlarmPlayback() {
        final NotificationManager notificationManager = (NotificationManager)
                getContext().getSystemService(NOTIFICATION_SERVICE);
        return notificationManager.getCurrentInterruptionFilter() !=
                NotificationManager.INTERRUPTION_FILTER_NONE;
    }
}
