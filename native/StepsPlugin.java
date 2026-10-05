package com.nikhil.commandcenter;

import android.Manifest;
import android.content.Context;
import android.os.Build;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.concurrent.TimeUnit;

@CapacitorPlugin(
    name = "Steps",
    permissions = {
        @Permission(alias = "activity", strings = { Manifest.permission.ACTIVITY_RECOGNITION })
    }
)
public class StepsPlugin extends Plugin {

    @Override
    public void load() {
        if (isGranted()) schedule();
    }

    private boolean isGranted() {
        if (Build.VERSION.SDK_INT < 29) return true;
        return getPermissionState("activity") == PermissionState.GRANTED;
    }

    private void schedule() {
        PeriodicWorkRequest req =
            new PeriodicWorkRequest.Builder(StepWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(getContext())
            .enqueueUniquePeriodicWork("step-readings", ExistingPeriodicWorkPolicy.KEEP, req);
    }

    private JSObject statusObject() {
        JSObject r = new JSObject();
        r.put("available", StepStore.hasSensor(getContext()));
        r.put("granted", isGranted());
        return r;
    }

    @PluginMethod
    public void status(PluginCall call) {
        call.resolve(statusObject());
    }

    @PluginMethod
    public void requestAccess(PluginCall call) {
        if (isGranted()) {
            schedule();
            call.resolve(statusObject());
            return;
        }
        requestPermissionForAlias("activity", call, "accessCallback");
    }

    @PermissionCallback
    private void accessCallback(PluginCall call) {
        if (isGranted()) schedule();
        call.resolve(statusObject());
    }

    @PluginMethod
    public void read(final PluginCall call) {
        if (!isGranted()) {
            call.reject("not_granted");
            return;
        }
        final Context c = getContext();
        new Thread(() -> {
            float v = StepStore.readCounter(c, 5000);
            if (v >= 0) StepStore.addSnapshot(c, System.currentTimeMillis(), v);
            JSObject r = new JSObject();
            r.put("value", (double) v);
            r.put("snapshots", StepStore.getSnapshots(c));
            call.resolve(r);
        }).start();
    }
}
