package com.example.robin.fmi_fotoapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.facebook.stetho.Stetho;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

// Imports for Speech Recognizer

/**
 * Created by Robin
 */

public class MainActivity extends AppCompatActivity{

    //ids for front and rear camera
    public static final String CAMERA_FRONT = "1";
    public static final String CAMERA_BACK = "0";

    //preference keys
    private static final String VOLUME_PREF_KEY = "Lautstärkewippe";
    private static final String SHAKE_PREF_KEY = "Schütteln";
    private static final String DOUBLE_TAP_PREF_KEY = "Doppelklick";
    private static final String SPEECH_PREF_KEY = "Spracheingabe";
    private static final String PULSE_PREF_KEY = "Pulssensor";
    private static final String GESTURE_PREF_KEY = "Gestenerkennung";
    private static final String EXPRESSION_PREF_KEY = "Mimik";
    private static final String DELAY_PREF_KEY = "Verzögerung";

    //preference values
    private int delay = 0;
    private boolean volumeTriggerActivated = false;
    private boolean shakeTriggerActivated = false;
    private boolean doubleTapTriggerActivated = false;
    private boolean speechTriggerActivated = false;
    private boolean pulseTriggerActivated = false;
    private boolean gestureTriggerActivated = false;
    private boolean expressionTriggerActivated = false;

    //related to speech recognation
    private SpeechRecognizerManager mSpeechRecognizerManager;

    /* Named searches allow to quickly reconfigure the decoder */
    private String KWS_SEARCH = "wakeup";
    /* Keyword we are looking for to activate menu */
    private String KEYPHRASE = "take a photo";
    private static final float KEYWORD_THRESHOLD = 1e-20f;
    private static final String ALL_PHONE_CI = "-allphone_ci";
    private static final String EN_US_PTM = "en-us-ptm";
    private static final String DICTIONARY = "cmudict-en-us.dict";
    private static final String TAG = SpeechRecognizerManager.class.getSimpleName();

    //related to shake detection
    private SensorManager sensorManager; //used to read sensors
    private final float SHAKE_THRESHOLD = 3.5f;
    private final int X_AXIS = 0;
    private final int Y_AXIS = 1;
    private final int Z_AXIS = 2;
    private boolean started = true;
    private boolean moved = false;
    private float xFirstAcceleration;
    private float yFirstAcceleration;
    private float zFirstAcceleration;
    private float xLastAcceleration;
    private float yLastAcceleration;
    private float zLastAcceleration;
    private boolean shaked = false;

    //gesture detector
    private GestureDetectorCompat mDetector;

    //permission related
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int REQUEST_EXTERNAL_STORAGE_PERMISSION_RESULT = 1;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION_RESULT = 2;
    private static final int REQUEST_BODY_SENSORS_PERMISSION_RESULT = 3;
    private static final int REQUEST_MULTIPLES_PERMISSION_RESULT = 4;
    private boolean microphonePermissionGranted = false;


    //shared preferences
    private String prefsFile;
    private SharedPreferences prefs;

    //buttons
    private ImageButton takePhotoButton;
    private ImageButton settingsButton;
    private ImageButton cameraSwitchButton;

    private boolean mFlashSupported = false;
    private boolean cAutoFocusSupported = false; //camera supports autofocus?

    //camera states
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    //

    private int cCaptureState = STATE_PREVIEW;
    private File cImageFolder;
    private String cImageFileName;
    private TextureView cPreview;
    private CameraDevice cDevice;
    private String cId = CAMERA_BACK; //camera id (rear camera is default)
    private Size cPreviewSize; // camera preview size
    private int cTotalRotation;
    private Size cImageSize;
    private ImageReader cImageReader;
    private final ImageReader.OnImageAvailableListener cOnImageAvailableListener = new ImageReader.OnImageAvailableListener(){

        @Override
        public void onImageAvailable(ImageReader imageReader) {
            cBackgroundHandler.post(new ImageSaver(imageReader.acquireLatestImage())); //save image from background thread
        }
    };

    private class ImageSaver implements Runnable{ //used to save images

        private final Image image;

        public ImageSaver(Image image){
            this.image = image;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);

            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(cImageFileName);
                fileOutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }finally { //cleanup
                image.close();
                if(fileOutputStream != null){
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            // Tell the media scanner about the new file so that it is
            // immediately available to the user.
            MediaScannerConnection.scanFile(getApplicationContext(), new String[] { cImageFileName }, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            //happy day the image is now available via gallery
                        }
                    });

        }
    }

    private CameraCaptureSession cPreviewCaptureSession;
    private CameraCaptureSession.CaptureCallback cPreviewCaptureCallback = new CameraCaptureSession.CaptureCallback(){

        private void process(CaptureResult captureResult){
            switch (cCaptureState){
                case STATE_PREVIEW:
                    break;
                case STATE_WAIT_LOCK:
                    Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                    if(afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_INACTIVE){
                        startCaptureRequest();
                    }
                    cCaptureState = STATE_PREVIEW;
                    break;
            }
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }
    };
    private CaptureRequest.Builder cCaptureRequestBuilder; //needed to capture a single image from the camera device

    //related to background thread
    private HandlerThread cBackgroundHandlerThread;
    private Handler cBackgroundHandler;
    private static final String THREAD_NAME = "FMI_Fotoapp";

    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static { //orientation list is used to convert the display orientations into a real world orientation percentage
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private CameraDevice.StateCallback cDeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            cDevice = cameraDevice;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            cDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            cDevice = null;
        }
    };
    private MySurfaceTextureListener cSurfaceListener = new MySurfaceTextureListener();
    private class MySurfaceTextureListener implements TextureView.SurfaceTextureListener {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            setupCamera(width, height);
            transformImage(width, height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            transformImage(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    }

    private static class CompareSizeByArea implements Comparator<Size> { //Helper class to compare different resolutions from the preview

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() / (long) rhs.getWidth() * rhs.getHeight());
        }
    }
    private MySensorListener pulseSensorListener = null;
    private MySensorListener shakeSensorListener = null;
    private class MySensorListener implements SensorEventListener{
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            Sensor sensor = sensorEvent.sensor;
            if(sensor.getType() == Sensor.TYPE_ACCELEROMETER) { //acceleration sensor detected
                updateAccelerationValues(
                        sensorEvent.values[X_AXIS],
                        sensorEvent.values[Y_AXIS],
                        sensorEvent.values[Z_AXIS]
                );

                moved = movementDetected();

                if (moved && !shaked) {
                    shaked = true;
                } else if (moved && shaked) {

                    if (shakeTriggerActivated) { //trigger option activated
                        if(delay < 1500) { //the shake trigger always has a min delay of 1000ms
                            delay = 1500;
                        }
                        takePhoto();
                        delay = prefs.getInt(DELAY_PREF_KEY, 0);
                    }


                } else if (!moved && shaked) {
                    shaked = false;
                }
            }else if(sensor.getType() == Sensor.TYPE_HEART_RATE){ //heart rate sensor detected
                if(pulseTriggerActivated) {
                    takePhoto();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }

        private void updateAccelerationValues(
                float xCurrentAcceleration,
                float yCurrentAcceleration,
                float zCurrentAcceleration) {

            if(started){
                //values are 0 at the beginning, set sensor data
                xFirstAcceleration = xCurrentAcceleration;
                yFirstAcceleration = yCurrentAcceleration;
                zFirstAcceleration = zCurrentAcceleration;

                started = false;
            } else {
                //replace with the last acceleration values
                xFirstAcceleration = xLastAcceleration;
                yFirstAcceleration = yLastAcceleration;
                zFirstAcceleration = zLastAcceleration;
            }

            //cache values
            xLastAcceleration = xCurrentAcceleration;
            yLastAcceleration = yCurrentAcceleration;
            zLastAcceleration = zCurrentAcceleration;
        }

        private boolean movementDetected() {
            final float xDifference = Math.abs(xFirstAcceleration - xLastAcceleration);
            final float yDifference = Math.abs(yFirstAcceleration - yLastAcceleration);
            final float zDifference = Math.abs(zFirstAcceleration - zLastAcceleration);

            return (
                    xDifference > SHAKE_THRESHOLD ||
                            yDifference > SHAKE_THRESHOLD ||
                            zDifference > SHAKE_THRESHOLD
            );
        }
    }

    //double tap implementation
    @Override
    public boolean onTouchEvent(MotionEvent event){
        if(mDetector != null) {
            mDetector.onTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }

    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener implements GestureDetector.OnDoubleTapListener {
        private static final String DEBUG_TAG = "Gestures";

        @Override
        public boolean onDown(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            if (doubleTapTriggerActivated) { //trigger option activated?
                takePhoto();
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                               float velocityX, float velocityY) {
            return true;
        }
    }

    // Speech Recognizer
    private class SpeechRecognizerManager {

        private Context mContext;
        private edu.cmu.pocketsphinx.SpeechRecognizer mPocketSphinxRecognizer;

        public SpeechRecognizerManager(Context context) {
            this.mContext = context;
            initPocketSphinx();
        }

        private void initPocketSphinx() {

            new AsyncTask<Void, Void, Exception>() {
                @Override
                protected Exception doInBackground(Void... params) {
                    try {
                        Assets assets = new Assets(mContext);

                        //Performs the synchronization of assets in the application and external storage
                        File assetDir = assets.syncAssets();

                        //Creates a new speech recognizer builder with default configuration
                        SpeechRecognizerSetup speechRecognizerSetup = SpeechRecognizerSetup.defaultSetup();

                        speechRecognizerSetup.setAcousticModel(new File(assetDir, EN_US_PTM))
                                .setDictionary(new File(assetDir, DICTIONARY))
                                .setKeywordThreshold(KEYWORD_THRESHOLD) // Threshold to tune for keyphrase to balance between false alarms and misses
                                .setBoolean(ALL_PHONE_CI, true);

                        //Creates a new SpeechRecognizer object based on previous set up.
                        mPocketSphinxRecognizer = speechRecognizerSetup.getRecognizer();

                        // Add Listener
                        mPocketSphinxRecognizer.addListener(new PocketSphinxRecognitionListener());

                        // Create keyword-activation search.
                        mPocketSphinxRecognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);


                    } catch (IOException e) {
                        return e;
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Exception result) {
                    if (result != null) {
                        Toast.makeText(mContext, "Failed to init pocketSphinxRecognizer ", Toast.LENGTH_SHORT).show();
                    } else {
                        switchSearch(KWS_SEARCH);
                    }
                }
            }.execute();

        }

        private void switchSearch(String searchName) {
            mPocketSphinxRecognizer.stop();

            if (searchName.equals(KWS_SEARCH)) {
                mPocketSphinxRecognizer.startListening(searchName);
            }
        }

        protected class PocketSphinxRecognitionListener implements edu.cmu.pocketsphinx.RecognitionListener {

            @Override
            public void onBeginningOfSpeech() {
            }

            /**
             * In partial result we get quick updates about current hypothesis. In
             * keyword spotting mode we can react here, in other modes we need to wait
             * for final result in onResult.
             */
            @Override
            public void onPartialResult(Hypothesis hypothesis) {
                if (hypothesis == null) {
                    return;
                }
                String text = hypothesis.getHypstr();
                if (text.equals(KEYPHRASE) && speechTriggerActivated) {
                    Toast.makeText(mContext, "You said:" + text, Toast.LENGTH_SHORT).show();
                    takePhoto();
                    switchSearch(KWS_SEARCH);
                }
            }

            @Override
            public void onResult(Hypothesis hypothesis) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            public void onError(Exception error) {
            }

            @Override
            public void onTimeout() {
            }
        }
    }
    //function to load preference values from the preference file
    private void loadPreferences(){
        volumeTriggerActivated = prefs.getBoolean(VOLUME_PREF_KEY, false);
        shakeTriggerActivated = prefs.getBoolean(SHAKE_PREF_KEY, false);
        doubleTapTriggerActivated = prefs.getBoolean(DOUBLE_TAP_PREF_KEY, false);
        speechTriggerActivated = prefs.getBoolean(SPEECH_PREF_KEY, false);
        pulseTriggerActivated = prefs.getBoolean(PULSE_PREF_KEY, false);
        gestureTriggerActivated = prefs.getBoolean(GESTURE_PREF_KEY, false);
        expressionTriggerActivated = prefs.getBoolean(EXPRESSION_PREF_KEY, false);
        delay = prefs.getInt(DELAY_PREF_KEY, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) { //app started
        if(savedInstanceState != null){
            cId = savedInstanceState.getString("cId") == null ? CAMERA_BACK : savedInstanceState.getString("cId");
        }
        super.onCreate(savedInstanceState);
        Stetho.initializeWithDefaults(this); //initialize Stetho debugging tool
        prefsFile = getResources().getString(R.string.preferenceFile); //get the name of the SharedPreferences file from the string resources
        prefs = getSharedPreferences(prefsFile, 0); //load SharedPreferences
        loadPreferences(); //load and set preference values
        setContentView(R.layout.activity_main);
        createImageFolder();

        cPreview = (TextureView) findViewById(R.id.cameraPreview); //get camera preview view
        takePhotoButton = (ImageButton) findViewById(R.id.takePhotoButton);
        settingsButton = (ImageButton) findViewById(R.id.settingsButton);
        cameraSwitchButton = (ImageButton) findViewById(R.id.cameraSwitchButton);

        takePhotoButton.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                takePhoto();
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                startActivity(new Intent(getApplicationContext(), Einstellungen.class));
            }
        });

        cameraSwitchButton.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                switchCamera();
            }
        });

        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE); //for shake detection

    }

    private void switchCamera(){ //switch between front and rear camera
        closeCamera();
        if (cId == CAMERA_BACK){
            cId = CAMERA_FRONT;
        }else if (cId == CAMERA_FRONT){
            cId = CAMERA_BACK;
        }
        openCamera();
    }

    private void transformImage(int width, int height){ //transform image when the camera is rotated
        if(cPreviewSize == null || cPreview == null){
            return;
        }
        Matrix matrix = new Matrix();
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        RectF textureRectF = new RectF(0, 0, width, height);
        RectF previewRectF = new RectF(0, 0, cPreviewSize.getHeight(), cPreviewSize.getWidth());
        float centerX = textureRectF.centerX();
        float centerY = textureRectF.centerY();
        if(rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270){
            previewRectF.offset(centerX - previewRectF.centerX(), centerY - previewRectF.centerY());
            matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float)width / cPreviewSize.getWidth(), (float)height / cPreviewSize.getHeight());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        cPreview.setTransform(matrix);
    }

    @Override
    protected void onPause() { //app paused
        closeCamera();
        stopBackgroundThread();
        super.onPause();
        if(shakeSensorListener != null){
            sensorManager.unregisterListener(shakeSensorListener); //unregister listener
        }
        if(pulseSensorListener != null){
            sensorManager.unregisterListener(pulseSensorListener);
        }

    }

    private void openCamera(){
        setupCamera(cPreview.getWidth(), cPreview.getHeight());
        transformImage(cPreview.getWidth(), cPreview.getHeight());
        connectCamera();
    }

    @Override
    protected void onResume() { // app resumed
        super.onResume();
        startBackgroundThread();
        if (cPreview.isAvailable()) {
            openCamera();
        } else {
            cPreview.setSurfaceTextureListener(cSurfaceListener); //set listener for camera preview view
        }

        loadPreferences(); //load and set preference values

        //permission handling for pulse and speech
        if(speechTriggerActivated && !pulseTriggerActivated){
            checkRecordAudioPermission(); //single permission request
        }else if(pulseTriggerActivated && !speechTriggerActivated){
            checkBodySensorPermission();
        }else if(speechTriggerActivated && pulseTriggerActivated){
            String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO, Manifest.permission.BODY_SENSORS};  //multiple permission request
            if(!hasPermissions(PERMISSIONS)){
                requestPermissions(PERMISSIONS, REQUEST_MULTIPLES_PERMISSION_RESULT);
            }
        }
        //

        if(doubleTapTriggerActivated){
            if(mDetector == null) {
                mDetector = new GestureDetectorCompat(this, new MyGestureListener()); //gesture detector
            }
        }else{
            if(mDetector != null) {
                mDetector = null;
            }
        }
        if(shakeTriggerActivated){
            if(shakeSensorListener == null){
                shakeSensorListener = new MySensorListener();
                //register event listener
                sensorManager.registerListener(
                        shakeSensorListener,
                        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                        SensorManager.SENSOR_DELAY_NORMAL
                );
            }
        }else{
            if(shakeSensorListener != null) {
                sensorManager.unregisterListener(shakeSensorListener);
                shakeSensorListener = null;
            }
        }

        if(sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null) {
            if(pulseSensorListener == null) {
                pulseSensorListener = new MySensorListener();
                sensorManager.registerListener(
                        pulseSensorListener,
                        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE),
                        SensorManager.SENSOR_DELAY_NORMAL
                );
            }
        }

        if(microphonePermissionGranted) {
            if(mSpeechRecognizerManager == null) {
                mSpeechRecognizerManager = new SpeechRecognizerManager(this); //speech recognition
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { //check permission request result
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION_RESULT){
            if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED){ //request was rejected?
                Toast.makeText(getApplicationContext(), "Diese App benötigt Kamerazugriff", Toast.LENGTH_SHORT).show();
            }
        }
        if(requestCode == REQUEST_EXTERNAL_STORAGE_PERMISSION_RESULT){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                try {
                    createImageFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else{
                Toast.makeText(getApplicationContext(), "Diese App benötigt Schreibzugriff auf External Storage", Toast.LENGTH_SHORT).show();
            }
        }
        if(requestCode == REQUEST_RECORD_AUDIO_PERMISSION_RESULT){
            if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED){ //request was rejected?
                Toast.makeText(getApplicationContext(), "Diese App benötigt Zugriff auf das Mikrofon für die Sprachsteuerung", Toast.LENGTH_LONG).show();
                microphonePermissionGranted = false;
            }else{
                microphonePermissionGranted = true;
            }
        }
        if(requestCode == REQUEST_BODY_SENSORS_PERMISSION_RESULT){
            if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED){ //request was rejected?
                Toast.makeText(getApplicationContext(), "Diese App benötigt Zugriff auf Body Sensores für den HRM Auslöser", Toast.LENGTH_LONG).show();
            }
        }
        if (requestCode == REQUEST_MULTIPLES_PERMISSION_RESULT){
            for(String permission : permissions){
                switch(permission){
                    case Manifest.permission.RECORD_AUDIO:
                        if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED){ //request was rejected?
                            Toast.makeText(getApplicationContext(), "Diese App benötigt Zugriff auf das Mikrofon für die Sprachsteuerung", Toast.LENGTH_LONG).show();
                            microphonePermissionGranted = false;
                        }else{
                            microphonePermissionGranted = true;
                        }
                        break;
                    case Manifest.permission.BODY_SENSORS:
                        if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED){ //request was rejected?
                            Toast.makeText(getApplicationContext(), "Diese App benötigt Zugriff auf Body Sensores für den HRM Auslöser", Toast.LENGTH_LONG).show();
                        }
                        break;
                }
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) { //full screen immersive mode
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if (hasFocus) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    private void setupCamera(int width, int height) { //setup the camera
        CameraManager cManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if(cManager.getCameraIdList().length == 1){ //only one camera was found
               cId = cManager.getCameraIdList()[0];
            }
            CameraCharacteristics cCharacteristics = cManager.getCameraCharacteristics(cId);

            //check for autofocus support
            int[] afAvailableModes = cCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);

            cAutoFocusSupported = !(afAvailableModes.length == 0 || (afAvailableModes.length == 1
                    && afAvailableModes[0] == CameraMetadata.CONTROL_AF_MODE_OFF));
            //

            // Check if the flash is supported.
            Boolean available = cCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            mFlashSupported = available == null ? false : available;
            //

            StreamConfigurationMap map = cCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            //swap between landscape and portrait
            int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
            int totalRotation = sensorToDeviceRotation(cCharacteristics, deviceOrientation);
            boolean swapRotation = totalRotation == 90 || totalRotation == 270;
            int rotatedWidth = width;
            int rotatedHeight = height;
            if (swapRotation) {
                rotatedWidth = height;
                rotatedHeight = width;
            }
            cTotalRotation = totalRotation;
            //***
            cPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
            cImageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG), rotatedWidth, rotatedHeight);
            cImageReader = ImageReader.newInstance(cImageSize.getWidth(), cImageSize.getHeight(), ImageFormat.JPEG, 2);
            cImageReader.setOnImageAvailableListener(cOnImageAvailableListener, cBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void connectCamera() { //connect to the device camera
        CameraManager cManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){ //Android Marshmallow or later?
                if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){ //camera permission check
                    cManager.openCamera(cId, cDeviceStateCallback, cBackgroundHandler);
                }else{
                    if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                        Toast.makeText(this, "Diese App benötigt Kamerazugriff", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT); //request camera permission
                }
            }else {
                cManager.openCamera(cId, cDeviceStateCallback, cBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview(){ //start camera preview
        SurfaceTexture surfaceTexture = cPreview.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(cPreview.getWidth(), cPreview.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            cCaptureRequestBuilder = cDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            cCaptureRequestBuilder.addTarget(previewSurface);

            cDevice.createCaptureSession(
                    Arrays.asList(previewSurface, cImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            cPreviewCaptureSession = cameraCaptureSession;
                            try {
                                cameraCaptureSession.setRepeatingRequest(cCaptureRequestBuilder.build(), null, cBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(getApplicationContext(), "Fehler beim Erstellen des Kamera Preview", Toast.LENGTH_SHORT).show();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void startCaptureRequest(){ //start capture request when taking a photo
        try {
            cCaptureRequestBuilder = cDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            cCaptureRequestBuilder.addTarget(cImageReader.getSurface());
            cCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, cTotalRotation);
            setAutoFlash(cCaptureRequestBuilder);

            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback(){

                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                    try {
                        createImageFileName();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            cPreviewCaptureSession.capture(cCaptureRequestBuilder.build(), captureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera(){ //close camera and free resources
        if(cDevice != null){
            cDevice.close();
            cDevice = null;
        }
    }

    private void startBackgroundThread(){
        cBackgroundHandlerThread = new HandlerThread(THREAD_NAME);
        cBackgroundHandlerThread.start();
        cBackgroundHandler = new Handler(cBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread(){
        cBackgroundHandlerThread.quitSafely();
        try{
            cBackgroundHandlerThread.join();
            cBackgroundHandlerThread = null;
            cBackgroundHandler = null;
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation){ //convert the display orientations into a real world orientation percentage
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height){ //choose optimal preview size
        List<Size> bigEnough = new ArrayList<Size>();
        for (Size option  : choices){
            if(option.getHeight() == option.getWidth() * height / width && option.getWidth() >= width && option.getHeight() >= height){
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0){
            return Collections.min(bigEnough, new CompareSizeByArea());
        }else{
            return choices[0];
        }
    }

    private void createImageFolder(){ //create folder to save images
        cImageFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FMI_Fotoapp");
        if (!cImageFolder.exists()) {
            cImageFolder.mkdirs();
        }
    }

    private File createImageFileName() throws IOException{ //create file name for newly created images
        String timestamp = new SimpleDateFormat("yyyymmdd_HHmmss").format(new Date());
        String prepend = "IMAGE_" + timestamp;
        File imageFile = new File(cImageFolder + "/" + prepend + ".jpg");
        cImageFileName = imageFile.getAbsolutePath();
        return imageFile;
    }

    private void checkWriteStoragePermission(){ //check if storage permission is set
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                if(shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                    Toast.makeText(this, "Diese APP benötigt Schreibzugriff auf External Storage!", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_EXTERNAL_STORAGE_PERMISSION_RESULT);
            }
        }else{
            try {
                createImageFileName();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean hasPermissions(String... permissions) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && permissions != null){
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    if(permission == Manifest.permission.RECORD_AUDIO){
                        microphonePermissionGranted = false;
                    }
                    return false;
                }else{
                    if(permission == Manifest.permission.RECORD_AUDIO){
                        microphonePermissionGranted = true;
                    }
                }
            }
        }
        microphonePermissionGranted = true;
        return true;
    }

    private void checkRecordAudioPermission(){ //check if storage permission is set
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
                if(shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)){
                    Toast.makeText(this, "Sprachsteuerung benötigt Zugriff auf Mikrofon", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_RECORD_AUDIO_PERMISSION_RESULT);
            }else{
                microphonePermissionGranted = true;
            }
        }else{
            microphonePermissionGranted = true;
        }
    }

    private void checkBodySensorPermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED){
                if(shouldShowRequestPermissionRationale(Manifest.permission.BODY_SENSORS)){
                    Toast.makeText(this, "HRM Auslöser benötigt Zugriff auf Body Sensors", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.BODY_SENSORS},REQUEST_BODY_SENSORS_PERMISSION_RESULT);
            }
        }
    }

    private void lockFocus(int delay){ //lock focus on what the camera is pointing at
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),"Fokus gesperrt",Toast.LENGTH_SHORT).show();
                cCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                cCaptureState = STATE_WAIT_LOCK;
                try {
                    cPreviewCaptureSession.capture(cCaptureRequestBuilder.build(), cPreviewCaptureCallback, cBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }, delay);
    }

    private void takePhoto(){ //use this function to take a photo
       checkWriteStoragePermission();
       if (cAutoFocusSupported) {
           lockFocus(delay);
       } else { //camera doesn't support autofocus
           Toast.makeText(getApplicationContext(), "Autofokus wird nicht unterstützt", Toast.LENGTH_SHORT).show();
       }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) { //when the volume control pad is pressed
            //Do something
            if (volumeTriggerActivated) {
                takePhoto();
            }
        }
        if ((keyCode == KeyEvent.KEYCODE_BACK)){ //when the back button is pressed
            finish(); //move app process to background
        }
        return true;
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) { //set auto flash
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putString("cId", cId);
    }

}
