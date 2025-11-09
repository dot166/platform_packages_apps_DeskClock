/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.deskclock.alarms;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.animation.PathInterpolatorCompat;

import com.android.deskclock.BaseActivity;
import com.android.deskclock.LogUtils;
import com.android.deskclock.R;
import com.android.deskclock.ThemeUtils;
import com.android.deskclock.Utils;
import com.android.deskclock.data.DataModel;
import com.android.deskclock.data.DataModel.AlarmVolumeButtonBehavior;
import com.android.deskclock.events.Events;
import com.android.deskclock.provider.AlarmInstance;
import com.android.deskclock.widget.CircleView;
import com.google.android.material.button.MaterialButton;

public class AlarmActivity extends BaseActivity
        implements View.OnClickListener {

    private static final LogUtils.Logger LOGGER = new LogUtils.Logger("AlarmActivity");

    private static final TimeInterpolator REVEAL_INTERPOLATOR =
            PathInterpolatorCompat.create(0.0f, 0.0f, 0.2f, 1.0f);
    private static final int ALERT_REVEAL_DURATION_MILLIS = 500;
    private static final int ALERT_FADE_DURATION_MILLIS = 500;
    private static final int ALERT_DISMISS_DELAY_MILLIS = 2000;

    private final Handler mHandler = new Handler(Looper.myLooper());
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            LOGGER.v("Received broadcast: %s", action);

            if (!mAlarmHandled) {
                switch (action) {
                    case AlarmService.ALARM_DONE_ACTION:
                        finish();
                        break;
                    default:
                        LOGGER.i("Unknown broadcast: %s", action);
                        break;
                }
            } else {
                LOGGER.v("Ignored broadcast: %s", action);
            }
        }
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LOGGER.i("Finished binding to AlarmService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            LOGGER.i("Disconnected from AlarmService");
        }
    };

    private AlarmInstance mAlarmInstance;
    private boolean mAlarmHandled;
    private AlarmVolumeButtonBehavior mVolumeBehavior;
    private int mCurrentHourColor;
    private boolean mReceiverRegistered;
    /** Whether the AlarmService is currently bound */
    private boolean mServiceBound;

    private ViewGroup mAlertView;
    private TextView mAlertTitleView;
    private TextView mAlertInfoView;

    private ViewGroup mContentView;
    private MaterialButton mSnoozeButton;
    private MaterialButton mDismissButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow the content to layout behind the status and navigation bars.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        setVolumeControlStream(AudioManager.STREAM_ALARM);
        final long instanceId = AlarmInstance.getId(getIntent().getData());
        mAlarmInstance = AlarmInstance.getInstance(getContentResolver(), instanceId);
        if (mAlarmInstance == null) {
            // The alarm was deleted before the activity got created, so just finish()
            LOGGER.e("Error displaying alarm for intent: %s", getIntent());
            finish();
            return;
        } else if (mAlarmInstance.mAlarmState != AlarmInstance.FIRED_STATE) {
            LOGGER.i("Skip displaying alarm for instance: %s", mAlarmInstance);
            finish();
            return;
        }

        LOGGER.i("Displaying alarm for instance: %s", mAlarmInstance);

        // Get the volume/camera button behavior setting
        mVolumeBehavior = DataModel.getDataModel().getAlarmVolumeButtonBehavior();

        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);

        // Hide navigation bar to minimize accidental tap on Home key
        hideNavigationBar();

        // Honor rotation on tablets; fix the orientation on phones.
        if (!getResources().getBoolean(R.bool.rotateAlarmAlert)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        }

        setContentView(R.layout.alarm_activity);

        mAlertView = findViewById(R.id.alert);
        mAlertTitleView = mAlertView.findViewById(R.id.alert_title);
        mAlertInfoView = mAlertView.findViewById(R.id.alert_info);

        mContentView = findViewById(R.id.content);
        mSnoozeButton = mContentView.findViewById(R.id.snooze);
        mDismissButton = mContentView.findViewById(R.id.dismiss);

        final TextView titleView = mContentView.findViewById(R.id.title);
        final TextClock digitalClock = mContentView.findViewById(R.id.digital_clock);

        titleView.setText(mAlarmInstance.getLabelOrDefault(this));
        Utils.setTimeFormat(digitalClock, false);

        mCurrentHourColor = ThemeUtils.resolveColor(this, android.R.attr.windowBackground);
        getWindow().setBackgroundDrawable(new ColorDrawable(mCurrentHourColor));

        mSnoozeButton.setOnClickListener(this);
        mDismissButton.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Re-query for AlarmInstance in case the state has changed externally
        final long instanceId = AlarmInstance.getId(getIntent().getData());
        mAlarmInstance = AlarmInstance.getInstance(getContentResolver(), instanceId);

        if (mAlarmInstance == null) {
            LOGGER.i("No alarm instance for instanceId: %d", instanceId);
            finish();
            return;
        }

        // Verify that the alarm is still firing before showing the activity
        if (mAlarmInstance.mAlarmState != AlarmInstance.FIRED_STATE) {
            LOGGER.i("Skip displaying alarm for instance: %s", mAlarmInstance);
            finish();
            return;
        }

        if (!mReceiverRegistered) {
            // Register to get the alarm done/snooze/dismiss intent.
            final IntentFilter filter = new IntentFilter(AlarmService.ALARM_DONE_ACTION);
            registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            mReceiverRegistered = true;
        }

        bindAlarmService();
    }

    @Override
    protected void onPause() {
        super.onPause();

        unbindAlarmService();

        // Skip if register didn't happen to avoid IllegalArgumentException
        if (mReceiverRegistered) {
            unregisterReceiver(mReceiver);
            mReceiverRegistered = false;
        }
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent keyEvent) {
        // Do this in dispatch to intercept a few of the system keys.
        LOGGER.v("dispatchKeyEvent: %s", keyEvent);

        final int keyCode = keyEvent.getKeyCode();
        switch (keyCode) {
            // Volume keys and camera keys dismiss the alarm.
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_FOCUS:
                if (!mAlarmHandled) {
                    switch (mVolumeBehavior) {
                        case SNOOZE:
                            if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                                snooze();
                            }
                            return true;
                        case DISMISS:
                            if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                                dismiss();
                            }
                            return true;
                    }
                }
        }
        return super.dispatchKeyEvent(keyEvent);
    }

    @Override
    public void onBackPressed() {
        // Don't allow back to dismiss.
    }

    @Override
    public void onClick(View view) {
        if (mAlarmHandled) {
            LOGGER.v("onClick ignored: %s", view);
            return;
        }
        LOGGER.v("onClick: %s", view);

        if (view == mSnoozeButton) {
            snooze();
        } else if (view == mDismissButton) {
            dismiss();
        }
    }

    private void hideNavigationBar() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /**
     * Perform snooze animation and send snooze intent.
     */
    private void snooze() {
        mAlarmHandled = true;
        LOGGER.v("Snoozed: %s", mAlarmInstance);

        final int snoozeMinutes = DataModel.getDataModel().getSnoozeLength();
        final String infoText = getResources().getQuantityString(
                R.plurals.alarm_alert_snooze_duration, snoozeMinutes, snoozeMinutes);
        final String accessibilityText = getResources().getQuantityString(
                R.plurals.alarm_alert_snooze_set, snoozeMinutes, snoozeMinutes);

        getAlertAnimator(mSnoozeButton, R.string.alarm_alert_snoozed_text, infoText,
                accessibilityText, Color.DKGRAY, mCurrentHourColor).start();

        AlarmStateManager.setSnoozeState(this, mAlarmInstance, false /* showToast */);

        Events.sendAlarmEvent(R.string.action_snooze, R.string.label_deskclock);

        // Unbind here, otherwise alarm will keep ringing until activity finishes.
        unbindAlarmService();
    }

    /**
     * Perform dismiss animation and send dismiss intent.
     */
    private void dismiss() {
        mAlarmHandled = true;
        LOGGER.v("Dismissed: %s", mAlarmInstance);

        getAlertAnimator(mDismissButton, R.string.alarm_alert_off_text, null /* infoText */,
                getString(R.string.alarm_alert_off_text) /* accessibilityText */,
                Color.DKGRAY, mCurrentHourColor).start();

        AlarmStateManager.deleteInstanceAndUpdateParent(this, mAlarmInstance);

        Events.sendAlarmEvent(R.string.action_dismiss, R.string.label_deskclock);

        // Unbind here, otherwise alarm will keep ringing until activity finishes.
        unbindAlarmService();
    }

    /**
     * Bind AlarmService if not yet bound.
     */
    private void bindAlarmService() {
        if (!mServiceBound) {
            final Intent intent = new Intent(this, AlarmService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            mServiceBound = true;
        }
    }

    /**
     * Unbind AlarmService if bound.
     */
    private void unbindAlarmService() {
        if (mServiceBound) {
            unbindService(mConnection);
            mServiceBound = false;
        }
    }

    private Animator getAlertAnimator(final View source, final int titleResId,
            final String infoText, final String accessibilityText, final int revealColor,
            final int backgroundColor) {
        final ViewGroup containerView = findViewById(android.R.id.content);

        final Rect sourceBounds = new Rect(0, 0, source.getHeight(), source.getWidth());
        containerView.offsetDescendantRectToMyCoords(source, sourceBounds);

        final int centerX = sourceBounds.centerX();
        final int centerY = sourceBounds.centerY();

        final int xMax = Math.max(centerX, containerView.getWidth() - centerX);
        final int yMax = Math.max(centerY, containerView.getHeight() - centerY);

        final float startRadius = Math.max(sourceBounds.width(), sourceBounds.height()) / 2.0f;
        final float endRadius = (float) Math.sqrt(xMax * xMax + yMax * yMax);

        final CircleView revealView = new CircleView(this)
                .setCenterX(centerX)
                .setCenterY(centerY)
                .setFillColor(revealColor);
        containerView.addView(revealView);

        // TODO: Fade out source icon over the reveal (like LOLLIPOP version).

        final Animator revealAnimator = ObjectAnimator.ofFloat(
                revealView, CircleView.RADIUS, startRadius, endRadius);
        revealAnimator.setDuration(ALERT_REVEAL_DURATION_MILLIS);
        revealAnimator.setInterpolator(REVEAL_INTERPOLATOR);
        revealAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                mAlertView.setVisibility(View.VISIBLE);
                mAlertTitleView.setText(titleResId);

                if (infoText != null) {
                    mAlertInfoView.setText(infoText);
                    mAlertInfoView.setVisibility(View.VISIBLE);
                }
                mContentView.setVisibility(View.GONE);

                getWindow().setBackgroundDrawable(new ColorDrawable(backgroundColor));
            }
        });

        final ValueAnimator fadeAnimator = ObjectAnimator.ofFloat(revealView, View.ALPHA, 0.0f);
        fadeAnimator.setDuration(ALERT_FADE_DURATION_MILLIS);
        fadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                containerView.removeView(revealView);
            }
        });

        final AnimatorSet alertAnimator = new AnimatorSet();
        alertAnimator.play(revealAnimator).before(fadeAnimator);
        alertAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                mAlertView.announceForAccessibility(accessibilityText);
                mHandler.postDelayed(() -> finish(), ALERT_DISMISS_DELAY_MILLIS);
            }
        });

        return alertAnimator;
    }
}
