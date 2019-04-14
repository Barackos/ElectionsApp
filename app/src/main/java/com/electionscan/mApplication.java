package com.electionscan;

import android.app.Application;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.database.FirebaseDatabase;

import io.fabric.sdk.android.Fabric;

public class mApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        //Firebase
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        //Crashlytics
        Fabric.with(this, new Crashlytics());
    }
}
