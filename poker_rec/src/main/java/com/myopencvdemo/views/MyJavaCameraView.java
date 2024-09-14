package com.myopencvdemo.views;

import android.content.Context;
import android.util.AttributeSet;

import org.opencv.android.JavaCameraView;

public class MyJavaCameraView extends JavaCameraView {
    public MyJavaCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyJavaCameraView(Context context, int cameraId) {
        super(context, cameraId);
    }

    public float getBitmapScale() {
        return mScale;
    }
}
