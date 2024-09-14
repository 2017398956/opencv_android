package com.myopencvdemo;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

import com.myopencvdemo.datapool.DataPool;
import com.myopencvdemo.domain.MlData;
import com.myopencvdemo.domain.RecResult;

import org.opencv.android.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.opencv.core.CvType.CV_8UC1;

public class PokerRecActivity extends Activity {
    private static final String TAG = "cv";

    private ArrayList<MatOfPoint> targetAreas = new ArrayList<>();
    private CameraBridgeViewBase.CvCameraViewListener2 cvCameraViewListener2 = new CameraBridgeViewBase.CvCameraViewListener2() {
        @Override
        public void onCameraViewStarted(int width, int height) {

        }

        @Override
        public void onCameraViewStopped() {

        }

        @Override
        public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
            try {
                targetAreas.clear();
                rects.clear();
                // 可能出现这个错误:(-215:Assertion failed) u != 0 in function 'void cv::Mat::create(int, const int*, int)'
                Mat gray = inputFrame.rgba();
                Mat srcMat = gray.clone();
                int twidth = srcMat.width();
                int theight = srcMat.height();
//                Log.e(App.tag, "target wh:" + twidth + "," + theight);
                // 识别区域
                Rect targetCropRect = new Rect();
                targetCropRect.width = twidth;
                int targetHeight = (int) (theight * (cropContentPercent * 1.0 / (cropContentPercent + coverContentPercennt * 2)));
                targetCropRect.height = targetHeight;
//            Log.e(App.tag, "target screen_width*screen_height:" + targetCropRect.width + "," + targetCropRect.height);
                targetCropRect.x = 0;
                targetCropRect.y = theight / 2 - targetCropRect.height / 2;
                // 获取识别区域的内容
                Mat targetMat = new Mat(srcMat, targetCropRect);
                Mat copyMat = targetMat.clone();
                // 灰度处理
                Imgproc.cvtColor(targetMat, targetMat, Imgproc.COLOR_RGBA2GRAY);
                Mat dst = new Mat(gray.rows(), gray.cols(), CV_8UC1);
                int bValue = seekBar.getProgress();
                // 二值化
                Imgproc.threshold(targetMat, dst, bValue, seekBar.getMax(), Imgproc.THRESH_BINARY);

                List<MatOfPoint> contours = new ArrayList<>();
                // CV_RETR_CCOMP, CV_CHAIN_APPROX_NONE
                Imgproc.findContours(dst, contours, new Mat(dst.rows(), dst.cols(), dst.type()),
                        Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

                ArrayList<RecResult> predicateds = new ArrayList<>();
                for (int i = 0; i < contours.size(); i++) {
                    MatOfPoint temp = contours.get(i);
                    // 使用 boundingRect 方法联通区域变成矩形区域
                    Rect rect = Imgproc.boundingRect(temp);
                    float wh = (rect.width * 1.0f / rect.height);//宽高比例
                    boolean test = false;
                    if ((test)
                            || (rect.height > (targetHeight * 1.0f / 5))
                            && (rect.height < 0.8f * targetHeight)
                            && (rect.width < (targetMat.width() / 8)) && (wh < 2)) {
//                   Log.e(App.tag, "------>>>rhfactor:" + wh + " startX:" + rect.x);
                        Imgproc.rectangle(copyMat, rect.tl(), rect.br(), new Scalar(0, 0, 255, 255), 2);
                        targetAreas.add(temp);
                        rects.add(rect);
                        if (checkBoxStudy.isChecked()) {
                            // 学习模式
                            Mat matArea = new Mat(dst, rect);
                            File dirFile = new File(EntryActivity.dataPath);
                            if (!dirFile.exists()) {
                                dirFile.mkdir();
                                Log.i(App.tag, "mkdir:" + dirFile.getAbsolutePath());
                            }
                            String path = EntryActivity.dataPath + File.separator + System.currentTimeMillis() + ".png";
                            Log.i(App.tag, "save path:" + path);
                            Bitmap bitmapMat = Bitmap.createBitmap(matArea.cols(), matArea.rows(), Bitmap.Config.RGB_565);
                            Utils.matToBitmap(matArea, bitmapMat);
                            try {
                                File tf = new File(path);
                                if (!tf.exists()) {
                                    tf.createNewFile();
                                }
                                bitmapMat.compress(Bitmap.CompressFormat.PNG, 75, new FileOutputStream(tf));
                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.i(App.tag, "保存失败" + path + " " + e.getLocalizedMessage());
                            }
                        } else {
                            if (needRec) {
                                // 识别模式,裁剪出候选区域
                                Mat matArea = new Mat(dst, rect);
                                Mat dstMat = new Mat(MlData.UNIT_WIDTH, MlData.UNIT_HEIGHT, matArea.type());
                                // 归一化，把所有的图片大小调整成一样大，得到的特征值才会是一样的
                                Imgproc.resize(matArea, dstMat, new Size(MlData.UNIT_WIDTH, MlData.UNIT_HEIGHT));
                                MatOfFloat matf = new MatOfFloat();
                                DataPool.getHogDescriptor().compute(dstMat, matf);//计算特征
//                        Log.e(App.tag, "matf row:" + matf.rows());
                                Mat nmatf = matf.reshape(0, 1);//修改特征值的为1行N列
                                Mat result = new Mat();
//                          Log.e(App.tag, "test col:" + nmatf.cols());
                                // 预测结果,这里的预测结果是一个数字，这个数字代表一个样本文件详情见初始化学习书序代码
                                float response = DataPool.getkNearest().predict(nmatf, result);
//                          Log.e(App.tag, "percent:" + DataPool.getPredicatedResult(response) + " predicated:" + nmatf.toString());
                                String resultStr = result.toString();
                                // 把数字转换成对应的文件,比如 /sdcard/0/0.png
                                String resultLabel = DataPool.getPredicatedResult(response);
                                File file = new File(resultLabel);
                                // 得到文件名 比如这个文件是 0.png，得到的就是 0,意味着识别的结果就是 0
                                String responnse = file.getParentFile().getName();
                                if (!"X".equalsIgnoreCase(responnse)) {//如果是 X 文件夹里面的，忽略之，否则添加到预测的结果中
//                                Log.e(App.tag, "识别结果：" + responnse);
                                    predicateds.add(new RecResult(responnse, rect.x, resultLabel));
                                }
//                            matArea.release();
//                            dstMat.release();
//                            matf.release();
//                            nmatf.release();
//                            result.release();
                            }
                        }
                    }
                }

                Collections.sort(predicateds, (o1, o2) -> {
                    if (o1.getStartX() <= o2.getStartX()) {
                        return -1;
                    } else {
                        return 1;
                    }
                });
                // 将花色和大小配对
                ArrayList<RecResult> huase = new ArrayList<>();//花色，
                ArrayList<RecResult> cardNo = new ArrayList<>();//对应花色的值
                for (int i = 0; i < predicateds.size(); i++) {
                    RecResult card = predicateds.get(i);
                    if (cardTypes.contains(card.getResultLabel())) {
                        huase.add(card);
                    } else {
                        cardNo.add(card);
                    }
                }
                ArrayList<Poker> pokers = new ArrayList<>();//存放识别结果
                StringBuffer sbfCardNo = new StringBuffer();
                // 正确的处理 10 的识别结果，如果只出现了 1 或者 0，识别结果都是不对的
                for (int i = 0; i < cardNo.size(); i++) {
                    if ("1".equalsIgnoreCase(cardNo.get(i).getResultLabel())) {
                        if ((i + 1) < cardNo.size()) {
                            String nextZero = cardNo.get(i + 1).getResultLabel();
                            if ("0".equalsIgnoreCase(nextZero)) {
                                // 1 和 0 不能分开，而且只有 '10' 这种情况
                                sbfCardNo.append(cardNo.get(i).getResultLabel()).append(cardNo.get(i + 1).getResultLabel()).append(",");
                                i++;
                            } else {
                                Log.e(App.tag, "识别 1 但是紧接着不是0【识别失败】");
                                break;
                            }
                        } else {
                            Log.e(App.tag, "识别到最后一个数字是1本次【识别失败】");
                            break;
                        }
                    } else {
                        sbfCardNo.append(cardNo.get(i).getResultLabel()).append(",");
                    }
                }

                String[] cards = sbfCardNo.toString().split(",");
                ArrayList<String> rightCardNo = new ArrayList<>();
                for (String card : cards) {
                    if (!"0".equals(card) && !"1".equals(card)) {
                        // 如果识别结果单独出现了1，或者 0 是不对的
                        rightCardNo.add(card);
                    }
                }
                // 经过处理后，如果和花色的数量是一样的，就算识别正确了
                // 筛选出正确的识别结果，到这里为止，算是识别成功
                Log.e(App.tag, "----------------------------Nice-------------------");
                final StringBuffer stringBuffer = new StringBuffer();
                for (int i = 0; i < rightCardNo.size(); i++) {
                    Poker poker = new Poker(huase.get(i).getResultLabel(), rightCardNo.get(i));
                    pokers.add(poker);
                    stringBuffer.append(poker);
                }
                Log.e(App.tag, "识别结果:" + stringBuffer);
                Log.e(App.tag, "----------------------------Nice End-------------------");
                runOnUiThread(() -> textViewResult.setText("识别结果:" + stringBuffer.toString() + "点击重新识别"));
                needRec = false;
                // 展示识别到的区域
                bitmap = Bitmap.createBitmap(targetCropRect.width, targetCropRect.height, Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(copyMat, bitmap);
                runOnUiThread(() -> imageViewRightTarget.setImageBitmap(bitmap));
                targetMat.release();
                return gray;
            } catch (Exception e) {
                Log.e(App.tag, "error onpreview:" + e.getLocalizedMessage());
            }
            return null;
        }
    };
    private JavaCameraView mOpenCvCameraView;
    private SeekBar seekBar;
    private CheckBox checkBoxCaerma;
    private CheckBox checkBoxStudy;
    private View viewContent;
    private ArrayList<String> cardTypes = new ArrayList<>();
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                Log.i(TAG, "OpenCV loaded successfully");
                mOpenCvCameraView.enableView();
            } else {
                super.onManagerConnected(status);
            }
        }
    };
    int screen_width;
    int screen_height;
    ArrayList<Rect> rects;
    Bitmap bitmap = null;
    ImageView imageViewRightTarget;
    private boolean needRec = true;
    private TextView textViewResult;
    /**
     * 识别区域高度在图片上所占的比重
     */
    private float cropContentPercent = 1;
    /**
     * 上半部分高度在图片上占的比重
     */
    private float coverContentPercennt = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.camera_preview_layout);

        imageViewRightTarget = findViewById(R.id.imageViewRightTarget);
        viewContent = findViewById(R.id.viewContent);
        viewContent.post(() -> {
            cropContentPercent = (((LinearLayout.LayoutParams) viewContent.getLayoutParams()).weight);
            coverContentPercennt = (((LinearLayout.LayoutParams) findViewById(R.id.converView).getLayoutParams()).weight);
            Log.d(App.tag, "weight is :" + cropContentPercent + " cover:" + coverContentPercennt * 2);
        });
        textViewResult = findViewById(R.id.textViewResult);
        textViewResult.setOnClickListener(v -> {
            needRec = true;
            textViewResult.setText("等待重新识别:");
        });

        rects = new ArrayList<>();
        checkBoxStudy = findViewById(R.id.checkBoxStudy);
        Resources resources = this.getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        screen_width = dm.widthPixels;
        screen_height = dm.heightPixels;

        seekBar = findViewById(R.id.seekBar);
        checkBoxCaerma = findViewById(R.id.checkBoxCaerma);
        checkBoxCaerma.setOnCheckedChangeListener((buttonView, isChecked) -> {
//                mOpenCvCameraView.setUseFrontCamera(isChecked);
            mOpenCvCameraView.setCameraIndex(isChecked ? 1 : 0);
            initCamera();
        });

        mOpenCvCameraView = findViewById(R.id.tutorial1_activity_java_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(cvCameraViewListener2);

        cardTypes.add("梅花");
        cardTypes.add("红桃");
        cardTypes.add("方块");
        cardTypes.add("黑桃");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        initCamera();
    }

    public void initCamera() {
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    static class Poker {

        String pokerType;
        String cardNo;

        public Poker(String pokerType, String cardNo) {
            this.pokerType = pokerType;
            this.cardNo = cardNo;
        }

        public String getPokerType() {
            return pokerType;
        }

        public void setPokerType(String pokerType) {
            this.pokerType = pokerType;
        }

        public String getCardNo() {
            return cardNo;
        }

        public void setCardNo(String cardNo) {
            this.cardNo = cardNo;
        }

        @NonNull
        @Override
        public String toString() {
            return "(" + pokerType + ":" + cardNo + ")";
        }
    }

}
