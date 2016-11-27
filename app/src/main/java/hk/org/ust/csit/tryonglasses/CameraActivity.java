package hk.org.ust.csit.tryonglasses;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;


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
import java.util.Date;
import java.util.List;

public class CameraActivity extends Activity implements SensorEventListener, CvCameraViewListener2  {

    private static final String TAG = "OCVSample::Activity";
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 0, 0, 0);
    public static final int JAVA_DETECTOR = 0;
    private static final int TM_SQDIFF = 0;
    private static final int TM_SQDIFF_NORMED = 1;
    private static final int TM_CCOEFF = 2;
    private static final int TM_CCOEFF_NORMED = 3;
    private static final int TM_CCORR = 4;
    private static final int TM_CCORR_NORMED = 5;
    private int learn_frames = 0;
    private Mat teplateRight,teplateLeft;
    int method = 0;
    private MenuItem mItemFace50;
    private MenuItem mItemFace40;
    private MenuItem mItemFace30;
    private MenuItem mItemFace20;
    private Mat mRgba,myMat,mGray;
    private File mCascadeFile;
    private File mCascadeFileEye;
    private CascadeClassifier mJavaDetector;
    private CascadeClassifier mJavaDetectorLeftEye;
    private CascadeClassifier mJavaDetectorRightEye;
    private int mDetectorType = JAVA_DETECTOR;
    private String[] mDetectorName;
    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;
    private CameraBridgeViewBase   mOpenCvCameraView;
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
    private static SoundPool soundPool;
    private static int beepID=0;
    private String mDirectory;
    private String fileName;
    Mat saveInPhoto;

    /** Populate the SoundPool*/
    public static void initSounds(Context context) {
        soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 5);
        beepID = soundPool.load(context, R.raw.beep07, 1);
    }

    // detect shake movement
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

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
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

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
                        cascadeDir.delete();

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
                        mJavaDetectorLeftEye = new CascadeClassifier(mCascadeFileEye.getAbsolutePath());
                        if (mJavaDetectorLeftEye.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier for eye");
                            mJavaDetectorLeftEye = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFileEye.getAbsolutePath());
                        cascadeDirEye.delete();


                        // load cascade file from application resources
                       ise = getResources().openRawResource(R.raw.haarcascade_righteye_2splits);
                        mCascadeFileEye = new File(cascadeDirEye, "haarcascade_righteye_2splits.xml");
                        ose = new FileOutputStream(mCascadeFileEye);

                        while ((bytesRead = ise.read(buffer)) != -1) {
                            ose.write(buffer, 0, bytesRead);
                        }
                        ise.close();
                        ose.close();
                        mJavaDetectorRightEye = new CascadeClassifier(mCascadeFileEye.getAbsolutePath());
                        if (mJavaDetectorRightEye.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier for eye");
                            mJavaDetectorRightEye = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFileEye.getAbsolutePath());
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

        //mMethodSeekbar = (SeekBar) findViewById(R.id.methodSeekBar);
        //mValue = (TextView) findViewById(R.id.method);

        /*
        mMethodSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {


            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {


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
        */
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
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        if (mAccel > 11) {
            Log.d("","Shake Shake");
            getGlassNo = getGlassNo +1;
            getGlassNo = getGlassNo % myImageList.size() ;
            realGlassNo = myImageList.get(getGlassNo);

            if(soundPool == null ){
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
        MatOfRect faces = new MatOfRect();
        if (mDetectorType == JAVA_DETECTOR) {
            if (mJavaDetector != null)
                mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2,
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
            for (int i = 0; i < facesArray.length; i++) {
                xCenter = (facesArray[i].x + facesArray[i].width + facesArray[i].x) / 2;
                yCenter = (facesArray[i].y + facesArray[i].y + facesArray[i].height) / 2;
                Rect r = facesArray[i];
                // split it
                Rect eyearea_right = new Rect(r.x + r.width / 16,
                        (int) (r.y + (r.height / 4.5)),
                        (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));
                Rect eyearea_left = new Rect(r.x + r.width / 16
                        + (r.width - 2 * r.width / 16) / 2,
                        (int) (r.y + (r.height / 4.5)),
                        (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));
                // draw the area - mGray is working grayscale mat, if you want to

                if (learn_frames < 5) {
                    teplateRight = get_template(mJavaDetectorLeftEye, eyearea_right, 24);
                    teplateLeft = get_template(mJavaDetectorLeftEye, eyearea_left, 24);
                    learn_frames++;
                    Log.d("Error", "Reach Frame");
                } else {
                    teplateRight = get_template(mJavaDetectorLeftEye, eyearea_right, 24);
                    teplateLeft = get_template(mJavaDetectorLeftEye, eyearea_left, 24);

                    Rect [] rightEyeArray = getEyeRec(mJavaDetectorLeftEye, eyearea_right, 24);
                    Rect [] leftEyeArray = getEyeRec(mJavaDetectorLeftEye, eyearea_left, 24);

                    if (rightEyeArray!=null && leftEyeArray!=null){
                        try {
                            double dist = Math.sqrt(Math.pow(((rightEyeArray[0].x+eyearea_right.x)-(leftEyeArray[0].x + eyearea_left.x)),2)+Math.pow((rightEyeArray[0].y+eyearea_right.y)-(leftEyeArray[0].y+eyearea_left.y),2));
                            myMat = Utils.loadResource(this, realGlassNo,  Imgcodecs.IMREAD_UNCHANGED);

                            Imgproc.cvtColor(myMat,myMat,Imgproc.COLOR_RGB2BGRA);

                            double leftEyeY=myMat.height() /2;
                            double rightEyeY=myMat.height() /2;
                            double leftEyeX=myMat.width()/4;
                            double rightEyeX=myMat.width()*3/4;
                            double glassDist = Math.sqrt(Math.pow((rightEyeX-leftEyeX),2)+Math.pow(rightEyeY-leftEyeY,2));
                            Log.d("Glass Distance between ", "dist = " + glassDist);
                            double factor = dist/glassDist;

                            Size dSize = new Size(myMat.width() * factor, myMat.height() * factor);
                            Mat resizeLens = new Mat();
                            Mat mask = Utils.loadResource(this, realGlassNo, -1);
                            Mat resizeMask = new Mat();
                            Imgproc.resize(myMat, resizeLens, dSize);
                            Imgproc.resize(mask, resizeMask, dSize);

                            List <Mat> fourMat = new ArrayList <>();
                            Core.split(resizeMask,fourMat);
                            Mat ImageAplhaMask = fourMat.get(3);

                            int posy =(int)(r.y + (yCenter - r.y - (rightEyeY*1.5 *factor)));
                            int posx =(int)(r.x + (xCenter - r.x - (rightEyeX *0.65* factor)));


                            resizeLens.copyTo(mRgba.submat(new Rect(posx, posy, resizeLens.width(), resizeLens.height())),ImageAplhaMask);
                            saveInPhoto=mRgba.clone();
                        }
                        catch(Exception ex){}
                    }
                }

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

            Core.MinMaxLocResult mmG = Core.minMaxLoc(mROI);

            iris.x = mmG.minLoc.x + eye_only_rectangle.x;
            iris.y = mmG.minLoc.y + eye_only_rectangle.y;
            eye_template = new Rect((int) iris.x - size / 2, (int) iris.y
                    - size / 2, size, size);

            template = (mGray.submat(eye_template)).clone();
            return template;
        }
        return template;
    }

    public void onRecreateClick(View v)
    {
        learn_frames = 0;
    }

    public void onCameraClick(View v)
    {
        takeScreenshot();
    }

    private void takeScreenshot() {
        Date now = new Date();
        android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now);
        try {
            //Mat saveInPhoto = mRgba.clone();
            //coz OpenCV reads images with blue , green and red channel instead of red,green, blue
            Imgproc.cvtColor(saveInPhoto, saveInPhoto, Imgproc.COLOR_BGR2RGB);

            String mPath = Environment.getExternalStorageDirectory().toString();
            mDirectory = mPath + "/Pictures/Screenshots";
            fileName = now + ".jpg";
            File folder = new File(mPath + "/Pictures/Screenshots");
            boolean success = true;
            if (!folder.exists()) {
                success = folder.mkdir();
            }
            if (success) {
                mPath = mPath + "/Pictures/Screenshots/" + fileName;
                success = Imgcodecs.imwrite(mPath, saveInPhoto);
                if (success) {
                   // Toast.makeText(getApplicationContext(), "File saved at " + mPath, Toast.LENGTH_SHORT).show();
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Do you want to share?");
                    builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //share to fb
                            final Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("image/jpg");
                            final File photoFile = new File(mDirectory, fileName);
                            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(photoFile));
                            startActivity(Intent.createChooser(shareIntent, "Share image using"));
                            dialog.dismiss();
                        }
                    });
                    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //TODO
                            dialog.dismiss();
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                } else
                    Toast.makeText(getApplicationContext(), "File saved failure " + mPath, Toast.LENGTH_SHORT).show();

                saveInPhoto.release();
            } else
                Toast.makeText(getApplicationContext(), "Create folder failure in location " + mPath + "/Pictures/Screenshots", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            // Several error may come out with file handling or OOM
            Toast.makeText(getApplicationContext(), e.toString(),
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();

        }
        /*
        try {
            Imgcodecs.imwrite( Environment.getExternalStorageDirectory().toString() + "/Pictures/Screenshots/" + now + "2.jpg", mRgba );
            // image naming and path  to include sd card  appending name you choose for file
            String mPath = Environment.getExternalStorageDirectory().toString() + "/Pictures/Screenshots/" + now + ".jpg";

            Toast.makeText(getApplicationContext(),mPath,
                    Toast.LENGTH_SHORT).show();

            // create bitmap screen capture
            View v1 = getWindow().getDecorView().getRootView();
            v1.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(v1.getDrawingCache());
            v1.setDrawingCacheEnabled(false);

            File imageFile = new File(mPath);

            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();

            //openScreenshot(imageFile);
        } catch (Throwable e) {
            // Several error may come out with file handling or OOM
            e.printStackTrace();
        }*/
    }

    private void openScreenshot(File imageFile) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        Uri uri = Uri.fromFile(imageFile);
        intent.setDataAndType(uri, "image/*");
        startActivity(intent);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

