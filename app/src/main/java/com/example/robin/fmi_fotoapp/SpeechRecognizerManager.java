package com.example.robin.fmi_fotoapp;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;


public class SpeechRecognizerManager {
    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "ok camera";
    private edu.cmu.pocketsphinx.SpeechRecognizer mPocketSphinxRecognizer;
    private static final String TAG = SpeechRecognizerManager.class.getSimpleName();
    private Context mContext;

    public SpeechRecognizerManager(Context context) {
        this.mContext = context;

        initPockerSphinx();
    }

    private void initPockerSphinx() {

        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(mContext);

                    //Performs the synchronization of assets in the application and external storage
                    File assetDir = assets.syncAssets();

                    //Creates a new speech recognizer builder with default configuration
                    SpeechRecognizerSetup speechRecognizerSetup = SpeechRecognizerSetup.defaultSetup();

                    speechRecognizerSetup.setAcousticModel(new File(assetDir, "en-us-ptm"));
                    speechRecognizerSetup.setDictionary(new File(assetDir, "cmudict-en-us.dict"));

                    // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                    //    speechRecognizerSetup.setRawLogDir(assetDir)

                    // Threshold to tune for keyphrase to balance between false alarms and misses
                    speechRecognizerSetup.setKeywordThreshold(1e-45f);

                    // Use context-independent phonetic search, context-dependent is too slow for mobile
                    speechRecognizerSetup.setBoolean("-allphone_ci", true);

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

        if (searchName.equals(KWS_SEARCH))
            mPocketSphinxRecognizer.startListening(searchName);

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
            if (hypothesis == null)
                return;


            String text = hypothesis.getHypstr();
            if (text.equals(KEYPHRASE)) {
                Toast.makeText(mContext, "You said:" + text, Toast.LENGTH_SHORT).show();

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



