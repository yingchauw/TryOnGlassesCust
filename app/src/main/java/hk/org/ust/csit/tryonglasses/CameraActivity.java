package hk.org.ust.csit.tryonglasses;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.opencv.imgcodecs.Imgcodecs.imread;

public class CameraActivity extends Activity implements SensorEventListener, CvCameraViewListener2  {

    private static final String    TAG                 = "OCVSample::Activity";
    private static final Scalar    FACE_RECT_COLOR     = new Scalar(0, 0, 0, 0);
    public static final int        JAVA_DETECTOR       = 0;
    private static final int TM_SQDIFF = 0;
    private static final int TM_SQDIFF_NORMED = 1;
    private static final int TM_CCOEFF = 2;
    private static final int TM_CCOEFF_NORMED = 3;
    private static final int TM_CCORR = 4;
    private static final int TM_CCORR_NORMED = 5;


    private int learn_frames = 0;
    private Mat teplateR;
    private Mat teplateL;
    int method = 0;

    // matrix for zooming
    private Mat mZoomWindow;
    private Mat mZoomWindow2;

    private MenuItem mItemFace50;
    private MenuItem               mItemFace40;
    private MenuItem               mItemFace30;
    private MenuItem               mItemFace20;
    // private MenuItem               mItemType;

    private Mat                    mRgba;
    private Mat                    mGray;
    private File mCascadeFile;
    private File                   mCascadeFileEye;
    private CascadeClassifier      mJavaDetector;
    private CascadeClassifier      mJavaDetectorEye;


    private int                    mDetectorType       = JAVA_DETECTOR;
    private String[]               mDetectorName;

    private float                  mRelativeFaceSize   = 0.2f;
    private int mAbsoluteFaceSize = 0;

    private CameraBridgeViewBase   mOpenCvCameraView;
    private SeekBar mMethodSeekbar;
    private TextView mValue;

    double xCenter = -1;
    double yCenter = -1;

    //shake sensor
    private SensorManager mSensorManager;
    private float mAccel; // acceleration apart from gravity
    private float mAccelCurrent; // current acceleration including gravity
    private float mAccelLast; // last acceleration including gravity
    private ArrayList<Integer> myImageList ;
    private int getGlassNo = 0;
    private int realGlassNo = 0;

    public static final int S1 = R.raw.beep07;
    private static SoundPool soundPool;
    private static HashMap soundPoolMap;
    private static int beepID=0;
    /** Populate the SoundPool*/
    public static void initSounds(Context context) {
        soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 5);
       beepID = soundPool.load(context, R.raw.beep07, 1);
        //soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 100);
        //soundPoolMap = new HashMap(1);
        //soundPoolMap.put( S1, soundPool.load(context, R.raw.beep, 1) );
    }

    private final SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent se) {
            float x = se.values[0];
            float y = se.values[1];
            float z = se.values[2];
            mAccelLast = mAccelCurrent;
            mAccelCurrent = (float) Math.sqrt((double) (x*x + y*y + z*z));
            float delta = mAccelCurrent - mAccelLast;
            mAccel = mAccel * 0.9f + delta; // perform low-cut filter
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO Auto-generated method stub

        }
    };


    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");


                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        // load cascade file from application resources
                        InputStream ise = getResources().openRawResource(R.raw.haarcascade_lefteye_2splits);
                        File cascadeDirEye = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFileEye = new File(cascadeDirEye, "haarcascade_lefteye_2splits.xml");
                        FileOutputStream ose = new FileOutputStream(mCascadeFileEye);

                        while ((bytesRead = ise.read(buffer)) != -1) {
                            ose.write(buffer, 0, bytesRead);
                        }
                        ise.close();
                        ose.close();

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

                        mJavaDetectorEye = new CascadeClassifier(mCascadeFileEye.getAbsolutePath());
                        if (mJavaDetectorEye.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier for eye");
                            mJavaDetectorEye = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFileEye.getAbsolutePath());

                        cascadeDir.delete();
                        cascadeDirEye.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }
                    mOpenCvCameraView.enableFpsMeter();
                    mOpenCvCameraView.setCameraIndex(1);
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public CameraActivity() {
        mDetectorName = new String[2];
        mDetectorName[JAVA_DETECTOR] = "Java";

        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        Log.i(TAG, "called onCreate");
        Intent testIntent = getIntent();
        myImageList=testIntent.getIntegerArrayListExtra("imageArray");

       /* myImageList.add(R.drawable.glasses8);
        myImageList.add(R.drawable.glasses6);
        myImageList.add(R.drawable.glasses3);
*/
        realGlassNo = myImageList.get(getGlassNo);

        //shake setting
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        mAccel = 0.00f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;

        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //setContentView(R.layout.face_detect_surface_view);
        setContentView(R.layout.activity_camera);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mMethodSeekbar = (SeekBar) findViewById(R.id.methodSeekBar);
        mValue = (TextView) findViewById(R.id.method);

        mMethodSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                // TODO Auto-generated method stub

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {
                // TODO Auto-generated method stub

            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser)
            {
                method = progress;
                switch (method) {
                    case 0:
                        mValue.setText("TM_SQDIFF");
                        break;
                    case 1:
                        mValue.setText("TM_SQDIFF_NORMED");
                        break;
                    case 2:
                        mValue.setText("TM_CCOEFF");
                        break;
                    case 3:
                        mValue.setText("TM_CCOEFF_NORMED");
                        break;
                    case 4:
                        mValue.setText("TM_CCORR");
                        break;
                    case 5:
                        mValue.setText("TM_CCORR_NORMED");
                        break;
                }


            }
        });
    }

    @Override
    public void onPause()
    {
        mSensorManager.unregisterListener(mSensorListener);
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }


    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
        mZoomWindow.release();
        mZoomWindow2.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        if (mAccel > 12) {
            Log.d("","Shake Shake");
            getGlassNo = getGlassNo +1;
            getGlassNo = getGlassNo % myImageList.size() ;
            realGlassNo = myImageList.get(getGlassNo);
            /*
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            Ringtone ringtoneSound = RingtoneManager.getRingtone(getApplicationContext(), ringtoneUri);

            if (ringtoneSound != null) {
                ringtoneSound.play();
            }*/
            if(soundPool == null || soundPoolMap == null){
                initSounds(getApplicationContext());
            }
            float volume = 1.0f;
            // whatever in the range = 0.0 to 1.0
            // play sound with same right and left volume, with a priority of 1,
            // zero repeats (i.e play once), and a playback rate of 1f
            int streamID = -1;
            do {
                streamID = soundPool.play(beepID, volume, volume, 1, 0, 1f);
            } while(streamID==0);
        }

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }

        }

        if (mZoomWindow == null || mZoomWindow2 == null)
            CreateAuxiliaryMats();

        MatOfRect faces = new MatOfRect();

        if (mDetectorType == JAVA_DETECTOR) {
            if (mJavaDetector != null)
                mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        }
        else {
            Log.e(TAG, "Detection method is not selected!");
        }

        Rect[] facesArray = faces.toArray();
        if(facesArray.length==0){
            //Log.d("No faceDetected", "Current learn_frames = "+learn_frames);
            learn_frames = 0;
        }
        else {
           // Log.d("Face Detected", "New learn_frames = "+learn_frames);

            for (int i = 0; i < facesArray.length; i++) {
                /*
                                    Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(),
                                    FACE_RECT_COLOR, 3);
                                   */
                xCenter = (facesArray[i].x + facesArray[i].width + facesArray[i].x) / 2;
                yCenter = (facesArray[i].y + facesArray[i].y + facesArray[i].height) / 2;
                Point center = new Point(xCenter, yCenter);

                //Imgproc.circle(mRgba, center, 10, new Scalar(255, 0, 0, 255), 3);

              /*  Imgproc.putText(mRgba, "[" + center.x + "," + center.y + "]",
                        new Point(center.x + 20, center.y + 20),
                        Core.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255, 255));
                        */
                Rect r = facesArray[i];
                // compute the eye area
                Rect eyearea = new Rect(r.x + r.width / 8,
                        (int) (r.y + (r.height / 4.5)), r.width - 2 * r.width / 8,
                        (int) (r.height / 3.0));

                //Log.d("Result = ", "r is " + r.toString() + " x = " + r.x + " , y = " + r.y + "width = " + r.width + " , heigh = " + r.height);
                // split it
                Rect eyearea_right = new Rect(r.x + r.width / 16,
                        (int) (r.y + (r.height / 4.5)),
                        (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));
                Rect eyearea_left = new Rect(r.x + r.width / 16
                        + (r.width - 2 * r.width / 16) / 2,
                        (int) (r.y + (r.height / 4.5)),
                        (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));
                // draw the area - mGray is working grayscale mat, if you want to

                // see area in rgb preview, change mGray to mRgba
                // Bitmap bmp = BitmapFactory.decodeFile(filename);
                //Bitmap bMap=BitmapFactory.decodeResource(getResources(),R.drawable.icon);
                //Mat imgToProcess = null;
                   /* try {
                        String filename = "C:\\Users\\yingc\\Desktop\\eyeTrackSample-master\\eyeTrackSample-master\\eyeTrackSample\\src\\main\\res\\drawable\\icon.png";




                        //Bitmap bMap=BitmapFactory.decodeFile(filename);
                        //bMap=makeBlackTransparent(bMap);

                        //Mat myMat = new Mat();
                        //Utils.bitmapToMat(bMap,myMat);

                        Mat myMat = Utils.loadResource(this, R.drawable.glasses2);


                        Size rSize = new Size(r.x, r.y);
                        Mat resizeLens = new Mat();
                        Imgproc.resize(myMat, resizeLens, rSize);

                        resizeLens.copyTo(mRgba.submat(new Rect(r.x, r.y, resizeLens.width(), resizeLens.height())));
                    }
                    catch(Exception e){

                    }*/

                //*/

                //Utils.bitmapToMat(bmp,myMat);

                    /*
                    Imgproc.rectangle(mRgba, eyearea_left.tl(), eyearea_left.br(),
                            new Scalar(255, 0, 0, 255), 2);
                    Imgproc.rectangle(mRgba, eyearea_right.tl(), eyearea_right.br(),
                            new Scalar(255, 0, 0, 255), 2);*/

                if (learn_frames < 5) {
                    teplateR = get_template(mJavaDetectorEye, eyearea_right, 24);
                    teplateL = get_template(mJavaDetectorEye, eyearea_left, 24);
                    learn_frames++;
                    Log.d("Error", "Reach Frame");
                } else {
                    // Learning finished, use the new templates for template
                    // matching

                    //match_eye(eyearea_right, teplateR, method);
                    //match_eye(eyearea_left, teplateL, method);

                    //get_template2(mJavaDetectorEye, eyearea_right, 24);
                    //get_template2(mJavaDetectorEye, eyearea_left, 24);

                    Rect [] rightEyeArray = getEyeRec(mJavaDetectorEye, eyearea_right, 24);
                    Rect [] leftEyeArray = getEyeRec(mJavaDetectorEye, eyearea_left, 24);
                    //Log.d("left eye area x" ,eyearea_left.x+","+eyearea_left.y);
                    if (rightEyeArray!=null && leftEyeArray!=null){
                        try {
                           // Log.d("Right eye info","Length = "+rightEyeArray.length +", x = "+rightEyeArray[0].x +", y = "+rightEyeArray[0].y);
                           // Log.d("Left eye info","Length = "+leftEyeArray.length +", x = "+leftEyeArray[0].x +", y = "+leftEyeArray[0].y);
                            double dist = Math.sqrt(Math.pow(((rightEyeArray[0].x+eyearea_right.x)-(leftEyeArray[0].x + eyearea_left.x)),2)+Math.pow((rightEyeArray[0].y+eyearea_right.x)-(leftEyeArray[0].y+eyearea_left.y),2));

                           // Log.d("Distance between ", "dist = " + dist);


                           Mat myMat = Utils.loadResource(this, realGlassNo, Imgcodecs.IMREAD_UNCHANGED);
                    //        Mat myMat = Utils.loadResource()
                        //    Imgproc.cvtColor(myMat,myMat,Imgproc.COLOR_RGB2BGRA);
                            double leftEyeY=myMat.height() /2;
                            double rightEyeY=myMat.height() /2;
                            double leftEyeX=myMat.width()/4;
                            double rightEyeX=myMat.width()*3/4;
                            double glassDist = Math.sqrt(Math.pow((rightEyeX-leftEyeX),2)+Math.pow(rightEyeY-leftEyeY,2));
                            Log.d("Glass Distance between ", "dist = " + glassDist);

                            double factor = dist/glassDist;

                        //    Size dsize = new Size(r.width, r.height);
                            Size dsize = new Size(myMat.width() * factor, myMat.height() * factor);
                            Mat resizeLens = new Mat();
                            Mat mask = Utils.loadResource(this, realGlassNo, 0);
                            Mat resizemask = new Mat();
                            Imgproc.resize(myMat, resizeLens, dsize);
                            Imgproc.resize(mask, resizemask, dsize);
                            resizeLens.copyTo(mRgba.submat(new Rect(eyearea_right.x, eyearea_right.y , resizeLens.width(), resizeLens.height())), resizemask);
                            //resizeLens.copyTo(mRgba.submat(new Rect(r.x, r.y, resizeLens.width(), resizeLens.height())), resizemask);
                        }
                        catch(Exception ex){}
                    }


                    /*
                    Point matLeftLoc = getEyeLocation(eyearea_left, teplateL, method);
                    Point matRightLoc = getEyeLocation(eyearea_right, teplateL, method);
                    if(matLeftLoc != null && matRightLoc!= null){
                        Log.d("Left Eye location = ", "result = "+matLeftLoc.x+","+matLeftLoc.y);
                        Log.d("Right Eye location = ", "result = "+matLeftLoc.x+","+matLeftLoc.y);
                        // original version
                        try {

                            Mat myMat = Utils.loadResource(this, R.drawable.glasses3);
                            Size dsize = new Size(r.width, r.height);
                            Mat resizeLens = new Mat();
                            Mat mask = Utils.loadResource(this, R.drawable.glasses3, 0);
                            Mat resizemask = new Mat();
                            Imgproc.resize(myMat, resizeLens, dsize);
                            Imgproc.resize(mask, resizemask, dsize);
                            resizeLens.copyTo(mRgba.submat(new Rect(r.x, r.y, resizeLens.width(), resizeLens.height())), resizemask);

                        } catch (Exception e) {
                            // Log.e(e.toString());
                        }
                    }
                    else{
                        Log.d("Eye location"," no  eye detection");
                    }
    */
                }

                /*
                        // cut eye areas and put them to zoom windows
                        Imgproc.resize(mRgba.submat(eyearea_left), mZoomWindow2,
                                mZoomWindow2.size());
                        Imgproc.resize(mRgba.submat(eyearea_right), mZoomWindow,
                                mZoomWindow.size());
                        */

            }
        }
        return mRgba;
    }
    private static Bitmap makeBlackTransparent(Bitmap image) {
        // convert image to matrix
        Mat src = new Mat(image.getWidth(), image.getHeight(), CvType.CV_8UC4);
        Utils.bitmapToMat(image, src);

        // init new matrices
        Mat dst = new Mat(image.getWidth(), image.getHeight(), CvType.CV_8UC4);
        Mat tmp = new Mat(image.getWidth(), image.getHeight(), CvType.CV_8UC4);
        Mat alpha = new Mat(image.getWidth(), image.getHeight(), CvType.CV_8UC4);

        // convert image to grayscale
        Imgproc.cvtColor(src, tmp, Imgproc.COLOR_BGR2GRAY);

        // threshold the image to create alpha channel with complete transparency in black background region and zero transparency in foreground object region.
        Imgproc.threshold(tmp, alpha, 100, 255, Imgproc.THRESH_BINARY);

        // split the original image into three single channel.
        List<Mat> rgb = new ArrayList<Mat>(3);
        Core.split(src, rgb);

        // Create the final result by merging three single channel and alpha(BGRA order)
        List<Mat> rgba = new ArrayList<Mat>(4);
        rgba.add(rgb.get(0));
        rgba.add(rgb.get(1));
        rgba.add(rgb.get(2));
        rgba.add(alpha);
        Core.merge(rgba, dst);

        // convert matrix to output bitmap
        Bitmap output = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(dst, output);
        return output;
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemFace50 = menu.add("Face size 50%");
        mItemFace40 = menu.add("Face size 40%");
        mItemFace30 = menu.add("Face size 30%");
        mItemFace20 = menu.add("Face size 20%");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item == mItemFace50)
            setMinFaceSize(0.5f);
        else if (item == mItemFace40)
            setMinFaceSize(0.4f);
        else if (item == mItemFace30)
            setMinFaceSize(0.3f);
        else if (item == mItemFace20)
            setMinFaceSize(0.2f);

        return true;
    }

    private void setMinFaceSize(float faceSize) {
        mRelativeFaceSize = faceSize;
        mAbsoluteFaceSize = 0;
    }

    private void CreateAuxiliaryMats() {
        if (mGray.empty())
            return;

        int rows = mGray.rows();
        int cols = mGray.cols();

        if (mZoomWindow == null) {
            mZoomWindow = mRgba.submat(rows / 2 + rows / 10, rows, cols / 2
                    + cols / 10, cols);
            mZoomWindow2 = mRgba.submat(0, rows / 2 - rows / 10, cols / 2
                    + cols / 10, cols);
        }

    }
    private Point getEyeLocation (Rect area, Mat mTemplate, int type) {
        Point matchLoc;
        Mat mROI = mGray.submat(area);
        int result_cols = mROI.cols() - mTemplate.cols() + 1;
        int result_rows = mROI.rows() - mTemplate.rows() + 1;
        // Check for bad template size
        if (mTemplate.cols() == 0 || mTemplate.rows() == 0) {
            return null;
        }
        Mat mResult = new Mat(result_cols, result_rows, CvType.CV_8U);

        switch (type) {
            case TM_SQDIFF:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_SQDIFF);
                break;
            case TM_SQDIFF_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult,
                        Imgproc.TM_SQDIFF_NORMED);
                break;
            case TM_CCOEFF:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCOEFF);
                break;
            case TM_CCOEFF_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult,
                        Imgproc.TM_CCOEFF_NORMED);
                break;
            case TM_CCORR:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCORR);
                break;
            case TM_CCORR_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult,
                        Imgproc.TM_CCORR_NORMED);
                break;
        }

        Core.MinMaxLocResult mmres = Core.minMaxLoc(mResult);
        // there is difference in matching methods - best match is max/min value
        if (type == TM_SQDIFF || type == TM_SQDIFF_NORMED) {
            matchLoc = mmres.minLoc;
        } else {
            matchLoc = mmres.maxLoc;
        }
        /*
        Point matchLoc_tx = new Point(matchLoc.x + area.x, matchLoc.y + area.y);
        Point matchLoc_ty = new Point(matchLoc.x + mTemplate.cols() + area.x,
                matchLoc.y + mTemplate.rows() + area.y);

        Imgproc.rectangle(mRgba, matchLoc_tx, matchLoc_ty, new Scalar(255, 255, 0,
                255));
        Rect rec = new Rect(matchLoc_tx,matchLoc_ty);
        */
        return matchLoc;
    }

    private void match_eye(Rect area, Mat mTemplate, int type) {
        Point matchLoc;
        Mat mROI = mGray.submat(area);
        int result_cols = mROI.cols() - mTemplate.cols() + 1;
        int result_rows = mROI.rows() - mTemplate.rows() + 1;
        // Check for bad template size
        if (mTemplate.cols() == 0 || mTemplate.rows() == 0) {
            return ;
        }
        Mat mResult = new Mat(result_cols, result_rows, CvType.CV_8U);

        switch (type) {
            case TM_SQDIFF:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_SQDIFF);
                break;
            case TM_SQDIFF_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult,
                        Imgproc.TM_SQDIFF_NORMED);
                break;
            case TM_CCOEFF:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCOEFF);
                break;
            case TM_CCOEFF_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult,
                        Imgproc.TM_CCOEFF_NORMED);
                break;
            case TM_CCORR:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCORR);
                break;
            case TM_CCORR_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult,
                        Imgproc.TM_CCORR_NORMED);
                break;
        }

        Core.MinMaxLocResult mmres = Core.minMaxLoc(mResult);
        // there is difference in matching methods - best match is max/min value
        if (type == TM_SQDIFF || type == TM_SQDIFF_NORMED) {
            matchLoc = mmres.minLoc;
        } else {
            matchLoc = mmres.maxLoc;
        }

        Point matchLoc_tx = new Point(matchLoc.x + area.x, matchLoc.y + area.y);
        Point matchLoc_ty = new Point(matchLoc.x + mTemplate.cols() + area.x,
                matchLoc.y + mTemplate.rows() + area.y);

        Imgproc.rectangle(mRgba, matchLoc_tx, matchLoc_ty, new Scalar(255, 255, 0,
                255));
        Rect rec = new Rect(matchLoc_tx,matchLoc_ty);


    }

    private Rect[] getEyeRec (CascadeClassifier clasificator, Rect area, int size) {
        Mat mROI = mGray.submat(area);
        MatOfRect eyes = new MatOfRect();
        clasificator.detectMultiScale(mROI, eyes, 1.15, 2,
                Objdetect.CASCADE_FIND_BIGGEST_OBJECT
                        | Objdetect.CASCADE_SCALE_IMAGE, new Size(30, 30),
                new Size());
        Rect[] eyesArray = eyes.toArray();
        if (eyesArray.length >0)
            return eyesArray;
        else
            return null;
    }

    private Mat get_template2(CascadeClassifier clasificator, Rect area, int size) {
        Mat template = new Mat();
        Mat mROI = mGray.submat(area);
        MatOfRect eyes = new MatOfRect();
        Point iris = new Point();
        Rect eye_template = new Rect();
        clasificator.detectMultiScale(mROI, eyes, 1.15, 2,
                Objdetect.CASCADE_FIND_BIGGEST_OBJECT
                        | Objdetect.CASCADE_SCALE_IMAGE, new Size(30, 30),
                new Size());

        Rect[] eyesArray = eyes.toArray();
        for (int i = 0; i < eyesArray.length;) {
            Rect e = eyesArray[i];
            e.x = area.x + e.x;
            e.y = area.y + e.y;
            Rect eye_only_rectangle = new Rect((int) e.tl().x,
                    (int) (e.tl().y + e.height * 0.4), (int) e.width,
                    (int) (e.height * 0.6));
            mROI = mGray.submat(eye_only_rectangle);
            Mat vyrez = mRgba.submat(eye_only_rectangle);


            Core.MinMaxLocResult mmG = Core.minMaxLoc(mROI);

            Imgproc.circle(vyrez, mmG.minLoc, 2, new Scalar(255, 255, 255, 255), 2);
            iris.x = mmG.minLoc.x + eye_only_rectangle.x;
            iris.y = mmG.minLoc.y + eye_only_rectangle.y;
            eye_template = new Rect((int) iris.x - size / 2, (int) iris.y
                    - size / 2, size, size);
            Imgproc.rectangle(mRgba, eye_template.tl(), eye_template.br(),
                    new Scalar(255, 0, 0, 255), 2);

            template = (mGray.submat(eye_template)).clone();
            return template;
        }
        return template;
    }

    private Mat get_template(CascadeClassifier clasificator, Rect area, int size) {
        Mat template = new Mat();
        Mat mROI = mGray.submat(area);
        MatOfRect eyes = new MatOfRect();
        Point iris = new Point();
        Rect eye_template = new Rect();
        clasificator.detectMultiScale(mROI, eyes, 1.15, 2,
                Objdetect.CASCADE_FIND_BIGGEST_OBJECT
                        | Objdetect.CASCADE_SCALE_IMAGE, new Size(30, 30),
                new Size());

        Rect[] eyesArray = eyes.toArray();
        for (int i = 0; i < eyesArray.length;) {
            Rect e = eyesArray[i];
            e.x = area.x + e.x;

            e.y = area.y + e.y;
            Rect eye_only_rectangle = new Rect((int) e.tl().x,
                    (int) (e.tl().y + e.height * 0.4), (int) e.width,
                    (int) (e.height * 0.6));
            mROI = mGray.submat(eye_only_rectangle);
            Mat vyrez = mRgba.submat(eye_only_rectangle);


            Core.MinMaxLocResult mmG = Core.minMaxLoc(mROI);

            //Imgproc.circle(vyrez, mmG.minLoc, 2, new Scalar(255, 255, 255, 255), 2);
            iris.x = mmG.minLoc.x + eye_only_rectangle.x;
            iris.y = mmG.minLoc.y + eye_only_rectangle.y;
            eye_template = new Rect((int) iris.x - size / 2, (int) iris.y
                    - size / 2, size, size);
            /*Imgproc.rectangle(mRgba, eye_template.tl(), eye_template.br(),
                    new Scalar(255, 0, 0, 255), 2);
                    */
            template = (mGray.submat(eye_template)).clone();
            return template;
        }
        return template;
    }

    public void onRecreateClick(View v)
    {
        learn_frames = 0;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

