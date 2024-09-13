package com.myopencvdemo;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.myopencvdemo.datapool.DataPool;
import com.myopencvdemo.domain.MlData;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.pedant.SweetAlert.SweetAlertDialog;

/**
 * 入口Activity
 */
public class EntryActivity extends AppCompatActivity {


    public static String targetpath = "pork";//学习模式收集的图片存放的位置
    public static String dataPath = Environment.getExternalStorageDirectory() + File.separator + targetpath;//学习模式收集的图片存放的位置

    public static String mldata = "good_data";//采集好的分类图片存放的位置
    public static String mldataPath = Environment.getExternalStorageDirectory() + File.separator + mldata;


    TextView textViewStatus;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                Log.i(App.tag, "OpenCV loaded successfully");
            } else {
                super.onManagerConnected(status);
            }
        }
    };


    private SweetAlertDialog pDialog;

    private void showProgressBar() {
        if (null != pDialog && pDialog.isShowing()) {
            pDialog.dismiss();
        }
        pDialog = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
        pDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
        pDialog.setTitleText("Loading");
        pDialog.setCancelable(false);
        pDialog.show();
    }

    private void hideProgressBar() {
        if (null != pDialog && pDialog.isShowing()) {
            pDialog.dismiss();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        textViewStatus = findViewById(R.id.textViewStatus);
        findViewById(R.id.buttonPreview).setOnClickListener(v -> startActivity(new Intent(this, PokerRecJiaoXueActivity.class)));

        findViewById(R.id.createMlData).setOnClickListener(v -> new MlThread(mldataPath).start());

        if (!OpenCVLoader.initDebug()) {
            Log.d(App.tag, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(App.tag, "OpenCV library found inside package. Using it!");
        }

    }


    class MlThread extends Thread {

        String path;

        public MlThread(String path) {
            this.path = path;
        }

        @Override
        public void run() {
            runOnUiThread(() -> {
                textViewStatus.setText("稍后");
                showProgressBar();
            });

            runOnUiThread(() -> textViewStatus.setText("拷贝样本数据到sdcard"));

            //拷贝数据
            File zipFile = new File("/sdcard/good_data.zip");
            if (!zipFile.exists()) {
                copyAssets(EntryActivity.this, "good_data.zip", "/sdcard/good_data.zip");
            }

            //解压缩
            File mldataPathFile = new File(mldataPath);
            if (!mldataPathFile.exists()) {
                unZip(zipFile, Environment.getExternalStorageDirectory() + File.separator);
                runOnUiThread(() -> textViewStatus.setText("解压完成"));
            }

            //初始化机器学习数据
            File file = new File(path);
            File[] fs = file.listFiles();
            DataPool.clear();
            for (File t : fs) {
                if (t.isFile()) {
                    continue;
                }
                MlData mlData = MlData.createMlDataFromDirectory(t);
                DataPool.addMlData(mlData.getLabel(), mlData);
            }
            DataPool.init();
            runOnUiThread(() -> {
                hideProgressBar();
                textViewStatus.setText("结束");
                Intent intent = new Intent(EntryActivity.this, PokerRecActivity.class);
                startActivity(intent);
            });

        }
    }

    /**
     * 复制asset文件到指定目录
     *
     * @param oldPath asset下的路径
     * @param newPath SD卡下保存路径
     */
    public static void copyAssets(Context context, String oldPath, String newPath) {
        try {
            String[] fileNames = context.getAssets().list(oldPath);// 获取assets目录下的所有文件及目录名
            if (fileNames == null) {
                return;
            }
            if (fileNames.length > 0) {// 如果是目录
                File file = new File(newPath);
                file.mkdirs();// 如果文件夹不存在，则递归
                for (String fileName : fileNames) {
                    copyAssets(context, oldPath + "/" + fileName, newPath + "/" + fileName);
                }
            } else {// 如果是文件
                InputStream is = context.getAssets().open(oldPath);
                FileOutputStream fos = new FileOutputStream(newPath);
                byte[] buffer = new byte[1024];
                int byteCount;
                while ((byteCount = is.read(buffer)) != -1) {// 循环从输入流读取
                    // buffer字节
                    fos.write(buffer, 0, byteCount);// 将读取的输入流写入到输出流
                }
                fos.flush();// 刷新缓冲区
                is.close();
                fos.close();
            }
        } catch (Exception e) {
            Log.e(App.tag, "拷贝出错：" + e.getLocalizedMessage());
        }
    }


    public static void unZip(File srcFile, String destDirPath) throws RuntimeException {
        long start = System.currentTimeMillis();
        // 判断源文件是否存在
        if (!srcFile.exists()) {
            throw new RuntimeException(srcFile.getPath() + "所指文件不存在");
        }

        // 开始解压
        ZipFile zipFile = null;

        try {
            zipFile = new ZipFile(srcFile);
            Enumeration<?> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                if (entry.isDirectory()) {
                    String dirPath = destDirPath + File.separator + entry.getName();
                    File dir = new File(dirPath);
                    dir.mkdirs();
                } else {
                    // 如果是文件，就先创建一个文件，然后用io流把内容copy过去
                    File targetFile = new File(destDirPath + File.separator + entry.getName());
                    // 保证这个文件的父文件夹必须要存在
                    if (!targetFile.getParentFile().exists()) {
                        targetFile.getParentFile().mkdirs();
                    }
                    targetFile.createNewFile();
                    // 将压缩文件内容写入到这个文件中
                    InputStream is = zipFile.getInputStream(entry);
                    FileOutputStream fos = new FileOutputStream(targetFile);
                    int len;
                    byte[] buf = new byte[1024];
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                    }
                    // 关流顺序，先打开的后关闭
                    fos.close();
                    is.close();
                }
            }
            long end = System.currentTimeMillis();
            System.out.println("解压完成，耗时：" + (end - start) + " ms");
        } catch (Exception e) {
            throw new RuntimeException("unzip error from ZipUtils", e);
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

    }
}
