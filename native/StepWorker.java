package com.nikhil.commandcenter;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Runs about every 15 minutes in the background and saves a step reading. */
public class StepWorker extends Worker {
    public StepWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        float v = StepStore.readCounter(c, 8000);
        if (v >= 0) StepStore.addSnapshot(c, System.currentTimeMillis(), v);
        return Result.success();
    }
}
