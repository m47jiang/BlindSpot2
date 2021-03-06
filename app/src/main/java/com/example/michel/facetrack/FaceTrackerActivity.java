/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.michel.facetrack;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Environment;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.example.michel.facetrack.camera.CameraSourcePreview;
import com.example.michel.facetrack.camera.GraphicOverlay;
import com.example.michel.facetrack.SentimentalAnalysis;
import com.google.android.gms.vision.face.Landmark;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.BlobProperties;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;

import org.w3c.dom.Text;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class FaceTrackerActivity extends AppCompatActivity {
    private static final String TAG = "FaceTracker";

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;

    private Button btnMicrophone;
    TextToSpeech mTTS;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    private final int SPEECH_RECOGNITION_CODE = 1;

    public static boolean flag_azure_done = false;
    public static boolean flag_comm_azure_and_api = false;

    public static boolean oksaid = false;

    public long lastFaceTime;
    public static boolean sixSecondFlag = false;

    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }

        mTTS=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    mTTS.setLanguage(Locale.CANADA);
//                    mTTS.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
//                        @Override
//                        public void onUtteranceCompleted(String utteranceId) {
//                            startSpeechToText();
//                        }
//                    });
                    String toSpeak = "Blind spot opened. What do you want?";
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                    waitStartSTT(8000);

                }

            }
        });

        /*mPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraSource.takePicture(new CameraSource.ShutterCallback() {
                    @Override
                    public void onShutter() {

                    }
                }, new CameraSource.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] bytes) {
                        String file_timestamp = Long.toString(System.currentTimeMillis());
                        Log.e("File: ", Environment.getExternalStorageDirectory() + "/" + file_timestamp + ".jpg");
                        final File file = new File(Environment.getExternalStorageDirectory() + "/" + file_timestamp + ".jpg");
                        try {
                            save(bytes, file);

                            String toSpeak = "Image saved";
                            mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                            Toast.makeText(FaceTrackerActivity.this, "Saved to " + Environment.getExternalStorageDirectory() + "/" + file_timestamp + ".jpg", Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    private void save(byte[] bytes, final File file) throws IOException {
                        OutputStream output = null;
                        try {
                            output = new FileOutputStream(file);
                            output.write(bytes);
                        } finally {
                            if (null != output) {
                                output.close();
                            }
                        }
                        Float happiness = sendPhotoToAzure(file); // Sending a blob (photo) to the Azure Storage
                        String photo_url = "https://blindspot.blob.core.windows.net/image/" + file.getName();
                        Log.e("Photo_url : ", photo_url);
//                        Float happiness = getHappiness(photo_url); // Call the Microsoft's Emotion API using the photo url
                        Log.e("Happiness: ", Float.toString(happiness));
                    }
                });
            }
        });*/

        lastFaceTime = System.currentTimeMillis();



    }


    public void waitStartSTT(final int millis) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    sleep(millis);
                    startSpeechToText();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    /**
     * Sends a file to Azure Storage
     */
    Float sendPhotoToAzure(final File file) {
        final String storageConnectionString =
                "DefaultEndpointsProtocol=https;" +
                        "AccountName=blindspot;" +
                        "AccountKey=D5xPtr7nFwZNqPzGZ96g29mQBPc4AqCcoVGarrsiWUPiYK9um8fJ3a2eVFHlpXu1Q1NZMdF4yasR+AIiRca7og==;";

        Thread thread = new Thread() {
            @Override
            public void run() {
                try
                {
                    // Retrieve storage account from connection-string.
                    CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString);

                    // Create the blob client.
                    CloudBlobClient blobClient = storageAccount.createCloudBlobClient();

                    // Retrieve reference to a previously created container.
                    CloudBlobContainer container = blobClient.getContainerReference("image");

                    // Define the path to a local file.

                    // Create or overwrite the "myimage.jpg" blob with contents from a local file.
                    CloudBlockBlob blob = container.getBlockBlobReference(file.getName());
//                    BlobProperties myProperties = blob.getProperties();
//                    myProperties.setContentMD5("-");
//                    blob.uploadProperties();
                    blob.upload(new FileInputStream(file), file.length());
                    flag_azure_done = true;
                    Log.e("InsideThreadAzure", " Sending picture");
                }
                catch (Exception e)
                {
                    // Output the stack trace.
                    e.printStackTrace();
                }
            }
        };
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

//        while(!flag_azure_done) {
//            try {
//                thread.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
        Log.e("OutsideThreadAzure", " Sent picture");

        SentimentalAnalysis sent = new SentimentalAnalysis();
        try {
            //"https://blindspot.blob.core.windows.net/image/123.jpg"
//            Float result_happiness = sent.post("https://blindspot.blob.core.windows.net/image/1485075198236.jpg");
            String photo_url = "https://blindspot.blob.core.windows.net/image/" + file.getName();
            Log.e("Photo_url : ", photo_url);
            Float result_happiness = sent.post(photo_url);
            System.out.println(result_happiness);
            return result_happiness;
        } catch (IOException e) {
            System.out.println("CRASH");
            return 0.0f;
        }
    }

    /**
     * Returns a happiness level of a picture url
     *
     */
    private Float getHappiness(String photo_url) {
        SentimentalAnalysis sent = new SentimentalAnalysis();
        try {
            //"https://blindspot.blob.core.windows.net/image/123.jpg"
            Float result_happiness = sent.post("https://blindspot.blob.core.windows.net/image/1485075198236.jpg");
            System.out.println(result_happiness);
            return result_happiness;
        } catch (IOException e) {
            System.out.println("CRASH");
            return 0.0f;
        } finally {
            flag_comm_azure_and_api = false;
        }
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);

            long currentFaceTime = System.currentTimeMillis();

            if(currentFaceTime - lastFaceTime > 3500 &&
                    (state == State.P_CONFIRMATION || state == State.S_CONFIRMATION)) {
                lastFaceTime = currentFaceTime;

                float eulerz = face.getEulerZ();
                if(eulerz > 5) {
                    updateState("tiltl");
                    return;
                } else if (eulerz < -5) {
                    updateState("tiltr");
                    return;
                }

                float width = face.getWidth(),
                    cameraWidth = mCameraSource.getPreviewSize().getWidth();
                if(width / cameraWidth > 0.4) {
                    updateState("close");
                    return;
                } else if (width / cameraWidth < 0.35) {
                    updateState("far");
                    return;
                }

                updateState("good");
            }
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }

    /**
     * Start speech to text intent. This opens up Google Speech Recognition API dialog box to listen the speech input.
     * */
    private void startSpeechToText() {
        Log.e("start speech to text", " start speech to text");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "Speak something...");
        try {
            startActivityForResult(intent, SPEECH_RECOGNITION_CODE);
            System.out.println("hello 2");
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    "Sorry! Speech recognition is not supported in this device.",
                    Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * Callback for speech recognition activity
     * */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        System.out.println("hello #");
        switch (requestCode) {
            case SPEECH_RECOGNITION_CODE: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    updateState(result.get(0));
                } else {
                    updateState("");
                }
                break;
            }
        }
    }

    //=====================================================

    private enum State {
        START, // photo or selfie
        SOK,
        POK,
        S_CONFIRMATION, // say ok after initial instruction
        P_CONFIRMATION,
        REQUEST_COMMENT,
        ADD_COMMENT,
        REQUEST_TAGS,
        ADD_TAGS,
        DONE
    }

    private State state = State.START;

    private void updateState(String response) {
        String toSpeak;

        if(response.isEmpty()) {
            toSpeak = "Please say your intention.";
            mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
            waitStartSTT(4000);
        }

        switch(state) {
            case START:
                try {
                    PostNLU.Intention intention = PostNLU.post(response);

                    if(intention.intent == PostNLU.Intent.TAKE && intention.photoType == PostNLU.PhotoType.SELFIE) {
                        state = State.S_CONFIRMATION;
                        toSpeak = "Hold the camera at eye level and arms length away.";
                        mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                        Thread thread = new Thread() {
                            @Override
                            public void run() {
                                try {
                                    sleep(5000);
                                    sixSecondFlag = true;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        };
                        thread.start();
                        while(sixSecondFlag != true) {

                        }
                        sixSecondFlag = false;
                    } else {
                        toSpeak = "Hold the camera at eye level.";
                        mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                        state = State.P_CONFIRMATION;
                        Thread thread = new Thread() {
                            @Override
                            public void run() {
                                try {
                                    sleep(4000);
                                    sixSecondFlag = true;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        };
                        thread.start();
                        while(sixSecondFlag != true) {

                        }
                        sixSecondFlag = false;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    toSpeak = "Error interpreting what you said. Please say it again.";
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                    waitStartSTT(4000);
                }
                break;
            case S_CONFIRMATION:
                if(response.equals("tiltr")) {
                    toSpeak = "Tilt camera slightly to the left.";
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                } else if (response.equals("tiltl")) {
                    toSpeak = "Tilt camera slightly to the right.";
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                } else if (response.equals("close")) {
                    toSpeak = "Move camera slightly farther away from yourself.";
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                } else if (response.equals("far")) {
                    toSpeak = "Move camera slightly closer towards yourself.";
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                } else if (response.equals("good")) {
                    state = State.REQUEST_COMMENT;
                    mCameraSource.takePicture(new CameraSource.ShutterCallback() {
                        @Override
                        public void onShutter() {

                        }
                    }, new CameraSource.PictureCallback() {
                        @Override
                        public void onPictureTaken(byte[] bytes) {
                            String file_timestamp = Long.toString(System.currentTimeMillis());
                            Log.e("File: ", Environment.getExternalStorageDirectory() + "/" + file_timestamp + ".jpg");
                            final File file = new File(Environment.getExternalStorageDirectory() + "/" + file_timestamp + ".jpg");
                            try {
                                save(bytes, file);
                                Toast.makeText(FaceTrackerActivity.this, "Saved to " + Environment.getExternalStorageDirectory() + "/" + file_timestamp + ".jpg", Toast.LENGTH_SHORT).show();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        private void save(byte[] bytes, final File file) throws IOException {
                            OutputStream output = null;
                            try {
                                output = new FileOutputStream(file);
                                output.write(bytes);
                            } finally {
                                if (null != output) {
                                    output.close();
                                }
                            }
                            sendPhotoToAzure(file); // Sending a blob (photo) to the Azure Storage
                            String photo_url = "https://blindspot.blob.core.windows.net/image/" + file.getName();
                            Log.e("Photo_url : ", photo_url);
                            Float happiness = getHappiness(photo_url); // Call the Microsoft's Emotion API using the photo url
                            Log.e("Happiness: ", Float.toString(happiness));
                        }
                    });
                    toSpeak = "Picture taken. Do you want to add a comment?";
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                    waitStartSTT(4000);
                }
                break;
            case P_CONFIRMATION:
                if(response.equals("tiltr")) {
                    toSpeak = "Tilt camera slightly to the right.";
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                } else if (response.equals("tiltl")) {
                    toSpeak = "Tilt camera slightly to the left.";
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                } else if (response.equals("close")) {
                    toSpeak = "Move camera slightly closer towards yourself.";
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                } else if (response.equals("far")) {
                    toSpeak = "Move camera slightly farther away from yourself.";
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                } else if (response.equals("good")) {
                    state = State.REQUEST_COMMENT;
                    toSpeak = "Picture taken. Do you want to add a comment?";
                    mCameraSource.takePicture(new CameraSource.ShutterCallback() {
                        @Override
                        public void onShutter() {

                        }
                    }, new CameraSource.PictureCallback() {
                        @Override
                        public void onPictureTaken(byte[] bytes) {
                            String file_timestamp = Long.toString(System.currentTimeMillis());
                            Log.e("File: ", Environment.getExternalStorageDirectory() + "/" + file_timestamp + ".jpg");
                            final File file = new File(Environment.getExternalStorageDirectory() + "/" + file_timestamp + ".jpg");
                            try {
                                save(bytes, file);
                                Toast.makeText(FaceTrackerActivity.this, "Saved to " + Environment.getExternalStorageDirectory() + "/" + file_timestamp + ".jpg", Toast.LENGTH_SHORT).show();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        private void save(byte[] bytes, final File file) throws IOException {
                            OutputStream output = null;
                            try {
                                output = new FileOutputStream(file);
                                output.write(bytes);
                            } finally {
                                if (null != output) {
                                    output.close();
                                }
                            }
                            sendPhotoToAzure(file); // Sending a blob (photo) to the Azure Storage
                            String photo_url = "https://blindspot.blob.core.windows.net/image/" + file.getName();
                            Log.e("Photo_url : ", photo_url);
                            Float happiness = getHappiness(photo_url); // Call the Microsoft's Emotion API using the photo url
                            Log.e("Happiness: ", Float.toString(happiness));
                        }
                    });
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                    waitStartSTT(4000);
                }
                break;
            case REQUEST_COMMENT:
                if(response.equalsIgnoreCase("yes")) {
                    state = State.ADD_COMMENT;
                    toSpeak = "Record comment now.";
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                    waitStartSTT(2000);
                } else if (response.equalsIgnoreCase("no")) {
                    toSpeak = "Storage complete. Goodbye.";
                    mTTS.setOnUtteranceProgressListener(exitListener);
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                } else {
                    toSpeak = "Error interpreting what you said. Please say it again.";
                    mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                    waitStartSTT(4000);
                }
                break;
            case ADD_COMMENT:
                toSpeak = "Storage complete. Goodbye";
                mTTS.setOnUtteranceProgressListener(exitListener);
                mTTS.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                Thread thread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            sleep(2000);
                            sixSecondFlag = true;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };
                thread.start();
                while(sixSecondFlag != true) {

                }
                System.exit(0);
                break;
            case DONE:
                break;
            default:
                //should not be here
        }
    }

    private UtteranceProgressListener exitListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {

        }

        @Override
        public void onDone(String utteranceId) {
            System.exit(0);
        }

        @Override
        public void onError(String utteranceId) {

        }
    };
}
