package com.nikhil.commandcenter;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Reads the phone's built-in step counter and keeps timestamped readings. */
public class StepStore {
    private static final String PREFS = "steps_store";
    private static final String KEY = "snaps";
    private static final long KEEP_MS = 5L * 24 * 60 * 60 * 1000;

    public static boolean hasSensor(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
    }

    /** Blocking read of the cumulative counter. Returns -1 on failure. Never call on the main thread. */
    public static float readCounter(Context c, long timeoutMs) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) return -1f;
        Sensor sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (sensor == null) return -1f;

        final float[] out = {-1f};
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerThread thread = new HandlerThread("step-read");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                out[0] = event.values[0];
                latch.countDown();
            }

            @Override
            public void onAccuracyChanged(Sensor s, int accuracy) { }
        };
        try {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler);
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        } finally {
            sm.unregisterListener(listener);
            thread.quitSafely();
        }
        return out[0];
    }

    public static synchronized void addSnapshot(Context c, long time, float value) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray all = new JSONArray(p.getString(KEY, "[]"));
            JSONObject o = new JSONObject();
            o.put("t", time);
            o.put("v", (double) value);
            all.put(o);
            JSONArray kept = new JSONArray();
            long cutoff = time - KEEP_MS;
            for (int i = 0; i < all.length(); i++) {
                JSONObject x = all.getJSONObject(i);
                if (x.getLong("t") >= cutoff) kept.put(x);
            }
            p.edit().putString(KEY, kept.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static synchronized String getSnapshots(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
    }
}
