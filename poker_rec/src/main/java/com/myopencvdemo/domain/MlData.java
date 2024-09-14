package com.myopencvdemo.domain;

import android.support.annotation.NonNull;
import android.util.Log;
import com.myopencvdemo.App;
import com.myopencvdemo.datapool.DataPool;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 一种类型的特征值数据，包括一系列的特征文件
 */
public class MlData {

    public static final int UNIT_WIDTH = 40, UNIT_HEIGHT = 60;

    /**
     * 当前特质类型文件的存放目录文件夹名称
     */
    public String label;

    public HashMap<File, MatOfFloat> datas;
    public ArrayList<MatOfFloat> listDatas;

    public MlData(String label) {
        this.label = label;
        this.datas = new HashMap<>();
        this.listDatas=new ArrayList<>();
    }

    public void putMlData(File file, MatOfFloat matOfFloat) {
        this.datas.put(file, matOfFloat);
        this.listDatas.add(matOfFloat);
    }

    public ArrayList<MatOfFloat> getAllMatdata(){
        return this.listDatas;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public HashMap<File, MatOfFloat> getDatas() {
        return datas;
    }

    public static MlData createMlDataFromDirectory(File dir) {
        MlData mlData = new MlData(dir.getName());
        if (dir.exists() && dir.isDirectory()) {
            File[] stdFiles = dir.listFiles();
            for (File df : stdFiles) {
                if (null != df && df.exists() && df.getAbsolutePath().toUpperCase().endsWith(".PNG")) {
                    MatOfFloat matOfFloat = createMlDataFromFile(df);
                    if (null != matOfFloat) {
                        mlData.putMlData(df, matOfFloat);
                    }
                }
            }
        } else {
            Log.e(App.tag, "study data must be a Directory");
        }

        return mlData;
    }

    private static MatOfFloat createMlDataFromFile(File file) {
        try {
            Mat mat = Imgcodecs.imread(file.getAbsolutePath());
            Mat dstMat = new Mat(UNIT_WIDTH, UNIT_HEIGHT, mat.type());
            Imgproc.resize(mat, dstMat, new Size(UNIT_WIDTH, UNIT_HEIGHT));

            MatOfFloat descriptors = new MatOfFloat();
            DataPool.getHogDescriptor().compute(dstMat, descriptors);
//          List<Float> list = descriptors.toList();
//          Log.i(App.tag, "create vector:" + file.getAbsolutePath());
            return descriptors;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @NonNull
    @Override
    public String toString() {
        return "label:"+label+" datasize:"+datas.size();
    }
}
