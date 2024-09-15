package com.myopencvdemo;

import android.app.Application;

import personal.nfl.permission.support.util.AbcPermission;

public class App extends Application {

    public static final String tag="pocker_rec";

    @Override
    public void onCreate() {
        super.onCreate();
        AbcPermission.install(this);
    }
}
