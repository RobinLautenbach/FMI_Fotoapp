package com.example.robin.fmi_fotoapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
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
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
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

import junit.framework.Test;

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

/**
 * Created by Robin
 */

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    //Sensoren auslesen (Shake)
    private SensorManager sensorManager;

    private final float SCHUETTEL_SCHWELLWERT = 3.5f;

    private final int X_ACHSE = 0;
    private final int Y_ACHSE = 1;
    private final int Z_ACHSE = 2;

    private boolean neuGestartet = true;
    private boolean bewegt = false;

    private float xErsteBeschleunigung;
    private float yErsteBeschleunigung;
    private float zErsteBeschleunigung;

    private float xLetzteBeschleunigung;
    private float yLetzteBeschleunigung;
    private float zLetzteBeschleunigung;

    private boolean geschuettelt = false;



    //Gesture Detector
    private GestureDetectorCompat mDetector;





    @Override
    public void onSensorChanged(SensorEvent event) {
        aktualisiereBeschleunigungswerte(
                event.values[X_ACHSE],
                event.values[Y_ACHSE],
                event.values[Z_ACHSE]
        );

        bewegt = bewegenErkannt();

        if(bewegt && !geschuettelt) {
            geschuettelt = true;
        } else if (bewegt && geschuettelt) {
            //FOTO MACHEN
            takePhoto();


        } else if (!bewegt && geschuettelt) {
            geschuettelt = false;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    protected void aktualisiereBeschleunigungswerte(
            float xAktuelleBeschleunigung,
            float yAktuelleBeschleunigung,
            float zAktuelleBeschleunigung) {

        if(neuGestartet){
            // werte zu beginn auf 0, setzen der tatsächlichen sensordaten
            xErsteBeschleunigung = xAktuelleBeschleunigung;
            yErsteBeschleunigung = yAktuelleBeschleunigung;
            zErsteBeschleunigung = zAktuelleBeschleunigung;

            neuGestartet = false;
        } else {
            //ersetzen durch die letzten beschleunigungswerte
            xErsteBeschleunigung = xLetzteBeschleunigung;
            yErsteBeschleunigung = yLetzteBeschleunigung;
            zErsteBeschleunigung = zLetzteBeschleunigung;
        }

        // aktuelle werte werden zwischengespeichert
        xLetzteBeschleunigung = xAktuelleBeschleunigung;
        yLetzteBeschleunigung = yAktuelleBeschleunigung;
        zLetzteBeschleunigung = zAktuelleBeschleunigung;
    }

    protected boolean bewegenErkannt() {
        final float xDifferenz = Math.abs(xErsteBeschleunigung - xLetzteBeschleunigung);
        final float yDifferenz = Math.abs(yErsteBeschleunigung - yLetzteBeschleunigung);
        final float zDifferenz = Math.abs(zErsteBeschleunigung - zLetzteBeschleunigung);

        return (
                xDifferenz > SCHUETTEL_SCHWELLWERT ||
                        yDifferenz > SCHUETTEL_SCHWELLWERT ||
                        zDifferenz > SCHUETTEL_SCHWELLWERT
        );
    }







    // DOUBLE TAP IMPLEMENTIERUNG

    @Override
    public boolean onTouchEvent(MotionEvent event){
        this.mDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    class MyGestureListener extends GestureDetector.SimpleOnGestureListener implements GestureDetector.OnDoubleTapListener {
        private static final String DEBUG_TAG = "Gestures";

        @Override
        public boolean onDown(MotionEvent event) {
            Log.d(DEBUG_TAG,"onDown: " +  "ASDFASFADFSDFAFADFADSF");

            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            Log.d(DEBUG_TAG,"onDoubleTap: "  + "DOUBLE TAAAAAAAAAAAAAP");

            takePhoto();
            return true;
        }


        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                               float velocityX, float velocityY) {
            Log.d(DEBUG_TAG, "onFling: " + event1.toString() + event2.toString());
            return true;
        }
    }































    private static final String VOLUME_PREF_KEY = "Lautstärkewippe";
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int REQUEST_EXTERNAL_STORAGE_PERMISSION_RESULT = 1;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private String prefsFile;
    private SharedPreferences prefs;
    private ImageButton takePhotoButton;
    private ImageButton settingsButton;
    private int cCaptureState = STATE_PREVIEW;
    private File cImageFolder;
    private String cImageFileName;
    private TextureView cPreview;
    private CameraDevice cDevice;
    private String cId; //camera id
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
                    cCaptureState = STATE_PREVIEW;
                    Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                    if(afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED){
                        startCaptureRequest();
                    }
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
    private HandlerThread cBackgroundHandlerThread;
    private Handler cBackgroundHandler;
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
    private TextureView.SurfaceTextureListener cSurfaceListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            setupCamera(width, height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private static class CompareSizeByArea implements Comparator<Size> { //Helper class to compare different resolutions from the preview

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() / (long) rhs.getWidth() * rhs.getHeight());
        }
    }








    @Override
    protected void onCreate(Bundle savedInstanceState) { //app started
        super.onCreate(savedInstanceState);
        Stetho.initializeWithDefaults(this); //initialize Stetho debugging tool
        prefsFile = getResources().getString(R.string.preferenceFile); //get the name of the SharedPreferences file from the string resources
        prefs = getSharedPreferences(prefsFile, 0); //load SharedPreferences
        setContentView(R.layout.activity_main);
        createImageFolder();

        cPreview = (TextureView) findViewById(R.id.cameraPreview); //get camera preview view
        takePhotoButton = (ImageButton) findViewById(R.id.takePhotoButton);
        settingsButton = (ImageButton) findViewById(R.id.settingsButton);
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


        //SensorManager für Shake
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);

        //Gesture Detector
        mDetector = new GestureDetectorCompat(this, new MyGestureListener());



    }

    @Override
    protected void onPause() { //app paused
        closeCamera();
        super.onPause();
        stopBackgroundThread();

        //Eventlistener unregistrieren
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() { // app resumed
        super.onResume();
        startBackgroundThread();
        if (cPreview.isAvailable()) {
            setupCamera(cPreview.getWidth(), cPreview.getHeight());
            connectCamera();
        } else {
            cPreview.setSurfaceTextureListener(cSurfaceListener); //set listener for camera preview view
        }

        //Eventlistener Registireren
        sensorManager.registerListener(
                (SensorEventListener) this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { //check permission request result
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION_RESULT){
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED){ //request was rejected?
                Toast.makeText(getApplicationContext(), "Diese App benötigt Kamerazugriff", Toast.LENGTH_SHORT).show();
            }
        }
        if(requestCode == REQUEST_EXTERNAL_STORAGE_PERMISSION_RESULT){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                try {
                    createImageFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else{
                Toast.makeText(getApplicationContext(), "Diese App benötigt Schreibzugriff auf External Storage!", Toast.LENGTH_SHORT).show();
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
            for (String mcId : cManager.getCameraIdList()) { //get list of device's camera ids
                CameraCharacteristics cCharacteristics = cManager.getCameraCharacteristics(mcId);
                if (cCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) { //front camera?
                    continue;
                }
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
                cId = mcId;
                return;
            }
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
        cBackgroundHandlerThread = new HandlerThread("FMI_Fotoapp");
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
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
              /*  try {
                    createImageFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }*/
            }else{
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

    private void lockFocus(){ //lock focus on what the camera is pointing at
        cCaptureState = STATE_WAIT_LOCK;
        cCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            cPreviewCaptureSession.capture(cCaptureRequestBuilder.build(), cPreviewCaptureCallback, cBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    public void takePhoto(){ //use this function to take a photo
        checkWriteStoragePermission();
        lockFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) { //when the volume control pad is pressed
            //Do something
            if (prefs.getBoolean(VOLUME_PREF_KEY, false)) {
                takePhoto();
            }
        }
        if ((keyCode == KeyEvent.KEYCODE_BACK)){ //when the back button is pressed
            finish(); //move app process to background
        }
        return true;
    }

}
