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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {

    public String TAG=this.getClass().getSimpleName();
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        private Typeface WATCH_TEXT_TYPEFACE = Typeface.create( Typeface.SERIF, Typeface.NORMAL );
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint,mDatePaint;
        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;
      //  Bitmap weatherBitmap;
      private int mTextColor = Color.parseColor( "white" );
        int high=0,low=0;
        private Paint mHighTemperaturePaint;
        private Paint mLowTemperaturePaint;
        Bitmap mWeatherIconBitmap;
        String mHighTemp;
        String mLowTemp;
        private boolean mRound;
        private SimpleDateFormat mDayOfWeekFormat;
        private Date mDate;
        private Time mDisplayTime;
        private static final String DATE_FORMAT = "%02d.%02d.%d";
        private boolean mBurnInProtection;
        Paint mHighTempPaint,mLowTempPaint,mWeatherIconPaint;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        GoogleApiClient mGoogleApiClient=new GoogleApiClient.Builder(MyWatchFace.this)
                .addApi(Wearable.API)
        .addConnectionCallbacks(this)
        .addOnConnectionFailedListener(this)
        .build();


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDatePaint = new Paint();
            mDatePaint.setColor(getResources().getColor(R.color.dullWhite));
            mDatePaint.setAntiAlias( true );
            mDatePaint.setTypeface( WATCH_TEXT_TYPEFACE );
            mDatePaint.setTextSize( getResources().getDimension( R.dimen.date_text_size ) );

            mLowTemperaturePaint = new Paint();
            mLowTemperaturePaint.setColor(getResources().getColor(R.color.dullWhite));
            mLowTemperaturePaint.setAntiAlias( true );
            mLowTemperaturePaint.setTypeface( WATCH_TEXT_TYPEFACE );
            mLowTemperaturePaint.setTextSize( getResources().getDimension( R.dimen.text_size ) );

            mHighTemperaturePaint = new Paint();
            mHighTemperaturePaint.setColor( mTextColor );
            mHighTemperaturePaint.setAntiAlias( true );
            mHighTemperaturePaint.setTypeface( WATCH_TEXT_TYPEFACE );
            mHighTemperaturePaint.setTextSize( getResources().getDimension( R.dimen.text_size ) );

            mWeatherIconPaint = new Paint();

            mCalendar = Calendar.getInstance();
            mDisplayTime = new Time();
            mDate = new Date();
            mWeatherIconBitmap=BitmapFactory.decodeResource(getResources(),R.drawable.ic_clear);
            mHighTemp="0";
            mLowTemp="0";
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
            if(insets.isRound())
            {
                mXOffset = getResources().getDimension( R.dimen.x_offset_round );
                mYOffset = getResources().getDimension( R.dimen.y_offset_round );
                mRound=true;
            }
            else
            {
                mXOffset = getResources().getDimension( R.dimen.x_offset_square );
                mRound=false;
            }
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.

            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));

            if(mRound)
            canvas.drawText(text, mXOffset+45, mYOffset+20, mTextPaint);
            else
            canvas.drawText(text,mXOffset,mYOffset+20,mTextPaint);


         //   mYOffset += 50+ getTextHeight(text,mTextPaint);

            String dateText = String.format(DATE_FORMAT, mDisplayTime.monthDay,(mDisplayTime.month + 1), mDisplayTime.year);


            mCalendar.setTimeInMillis(now);

            mDate.setTime(now);

            float y = getTextHeight(dateText, mTextPaint) + mYOffset +10;

            y += getTextHeight(dateText, mTextPaint);
            mXOffset = getResources().getDimension( R.dimen.x_offset );
            float x =mXOffset;

            mDayOfWeekFormat = new SimpleDateFormat("EEE, dd MMM", Locale.getDefault());

            mDayOfWeekFormat.setCalendar(mCalendar);
            int thisYear = mCalendar.get(Calendar.YEAR);
            String dayString = mDayOfWeekFormat.format(mDate)+" "+thisYear ;

            x = (bounds.width() - mDatePaint.measureText(dayString)) / 2;

            canvas.drawText(dayString.toUpperCase(), x, y, mDatePaint);


            //temperature

            int dummy = 0;
            if (mAmbient) {
                if (!mLowBitAmbient && !mBurnInProtection) {
                    dummy = 2;
                }
            } else {
                dummy = 1;
            }

            if (dummy > 0) {

                y = getTextHeight(dayString, mDatePaint) + mYOffset +50;

                y += getTextHeight(text, mDatePaint);

                x = mXOffset;

                x = (bounds.width() - (mWeatherIconBitmap.getWidth() + 20 + mTextPaint.measureText(mHighTemp))) / 2;

                if (dummy == 1)
                {
                    if(!isInAmbientMode())
                        canvas.drawBitmap(mWeatherIconBitmap, x, y, mWeatherIconPaint);

                }


                x += mWeatherIconBitmap.getWidth() + 5;
                y = y + mWeatherIconBitmap.getHeight() / 2;
                canvas.drawText(mHighTemp, x, y - 5, mHighTemperaturePaint);
                y += getTextHeight(mHighTemp, mTextPaint);
                canvas.drawText(mLowTemp, x, y + 5, mLowTemperaturePaint);
            }


        }

        private int getTextHeight(String text, Paint mTextPaint) {
            Rect rect = new Rect();
            mTextPaint.getTextBounds(text, 0, text.length(), rect);
            return rect.height();
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected( Bundle bundle) {
            Log.e(TAG, "onConnected: " );
            Wearable.DataApi.addListener(mGoogleApiClient,Engine.this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.e(TAG, "onConnectionSuspended: " );
        }

        @Override
        public void onConnectionFailed( ConnectionResult connectionResult) {
            Log.e(TAG, "onConnectionFailed: " );
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {

            Log.e(TAG, "onDataChanged: " );
            DataMap dataMap;
            for(DataEvent dataEvent:dataEventBuffer)
            {
                if(dataEvent.getType()==DataEvent.TYPE_CHANGED){
                    String path=dataEvent.getDataItem().getUri().getPath();
                    if(path.equals("/wearable_data"))
                    {
                        dataMap= DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                         high=(int)Math.round(dataMap.getDouble("maxTemp"));
                        low= (int)Math.round(dataMap.getDouble("minTemp"));
                        long id= dataMap.getLong("weatherId");
                        double timestamp= dataMap.getLong("timestamp");

                        mHighTemp = String.valueOf(high) + "° C";
                        mLowTemp =  String.valueOf(low) + "° C";

                        int iconId=getIconResourceForWeatherCondition((int) id);
                        Log.e(TAG, "onDataChanged: IconId"+iconId );
                        mWeatherIconBitmap=BitmapFactory.decodeResource(getResources(),iconId);
                     /*   Asset asset=dataMap.getAsset("weatherImage");
                        mWeatherIconBitmap=getBitmapFromAsset(asset);*/

                        Log.e(TAG, "onDataChanged: high"+high );
                        Log.e(TAG, "onDataChanged: low"+low );
                        Log.e(TAG, "onDataChanged: id"+id );
                        Log.e(TAG, "onDataChanged: timestamp"+timestamp );
                        invalidate();
                        continue;
                    }
                }
            }


        }

        private int getIconResourceForWeatherCondition(int weatherId) {
            if (weatherId >= 200 && weatherId <= 232) {
                return R.drawable.ic_storm;
            } else if (weatherId >= 300 && weatherId <= 321) {
                return R.drawable.ic_light_rain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                return R.drawable.ic_rain;
            } else if (weatherId == 511) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                return R.drawable.ic_rain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                return R.drawable.ic_fog;
            } else if (weatherId == 761 || weatherId == 781) {
                return R.drawable.ic_storm;
            } else if (weatherId == 800) {
                return R.drawable.ic_clear;
            } else if (weatherId == 801) {
                return R.drawable.ic_light_clouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                return R.drawable.ic_cloudy;
            }
            return -1;
        }

       /* private Bitmap getBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            ConnectionResult result =
                    mGoogleApiClient.blockingConnect(100, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                return null;
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    mGoogleApiClient, asset).await().getInputStream();
            mGoogleApiClient.disconnect();

            if (assetInputStream == null) {
                Log.w(TAG, "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }*/
    }
}
