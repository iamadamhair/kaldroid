package edu.tamu.adamhair.kaldroid;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.nio.*;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.Config;
import edu.cmu.pocketsphinx.Decoder;
import edu.cmu.pocketsphinx.Segment;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static android.widget.Toast.makeText;
import static junit.framework.Assert.fail;

public class MainActivity extends AppCompatActivity implements RecognitionListener {

    private enum LanguageModel {
        ENGLISH,
        ENGLISH_DYSARTHRIC,
        ENGLISH_CHILDREN,
        ENGLISH_DYSARTHRIC_CHILDREN
    };

    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    private static final String FORECAST_SEARCH = "forecast";
    private static final String DIGITS_SEARCH = "digits";
    private static final String PHONE_SEARCH = "phones";
    private static final String MENU_SEARCH = "menu";

    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "oh mighty computer";

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private SpeechRecognizer recognizer;
    private HashMap<String, Integer> captions;

    Button enButton;
    Button enDysButton;
    Button enKidButton;
    Button enDysKidButton;
    TextView infoLabel;
    ProgressBar pBar;
    boolean ignoreButtons = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        enButton = (Button) findViewById(R.id.en_us);
        enDysButton = (Button) findViewById(R.id.en_us_dys);
        enKidButton = (Button) findViewById(R.id.en_us_kid);
        enDysKidButton = (Button) findViewById(R.id.en_us_dys_kid);
        infoLabel = (TextView) findViewById(R.id.infoLabel);
        pBar = (ProgressBar) findViewById(R.id.progressBar);

        infoLabel.setText("Select a language model to process");

        // Check if user has given permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }
        //runRecognizerSetup();

        enButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleButtonPress(LanguageModel.ENGLISH);
            }
        });

        enDysButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleButtonPress(LanguageModel.ENGLISH_DYSARTHRIC);
            }
        });

        enKidButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleButtonPress(LanguageModel.ENGLISH_CHILDREN);
            }
        });

        enDysKidButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleButtonPress(LanguageModel.ENGLISH_DYSARTHRIC_CHILDREN);
            }
        });
    }

    private void handleButtonPress(LanguageModel model) {
        if (!ignoreButtons) {
            // Don't take button input until one has finished
            ignoreButtons = true;

            switch(model) {
                case ENGLISH:
                    infoLabel.setText("Processing with English model");
                    processAudio();
                    break;
                case ENGLISH_DYSARTHRIC:
                    infoLabel.setText("Processing with English/Dysarthric model");
                    processAudio();
                    break;
                case ENGLISH_CHILDREN:
                    infoLabel.setText("Processing with English/Kid model");
                    processAudio();
                    break;
                case ENGLISH_DYSARTHRIC_CHILDREN:
                    infoLabel.setText("Processing with English/Dysarthric/Kid model");
                    processAudio();
                    break;
                default:
                    infoLabel.setText("Button did not work. Try again.");
                    ignoreButtons = false;
                    break;
            }
        }
    }

    private void processAudio() {
        pBar.setVisibility(View.VISIBLE);

        // Process audio with PocketSphinx and appropriate model


        // Processing is done, accept input again
        ignoreButtons = false;
        pBar.setVisibility(View.INVISIBLE);
        infoLabel.setText("Select a language model to process");
    }

    private void processAudioFile(String filename) {
        Config c = Decoder.defaultConfig();
        c.setString("-hmm", "../../model/en-us/en-us");
        c.setString("-lm", "../../model/en-us/en-us.lm.dmp");
        c.setString("-dict", "../../model/en-us/cmudict-en-us.dict");
        Decoder d = new Decoder(c);
        FileInputStream stream = null;

        String filenameAndPath = Environment.getExternalStorageDirectory().getPath() + "/testAudio/" + filename;

        try {
            stream = new FileInputStream(new File(filenameAndPath));
        } catch (Exception e) {
            // Handle exception
        }

        d.startUtt();
        byte[] b = new byte[4096];
        try {
            int nbytes;
            while ((nbytes = stream.read(b)) >= 0) {
                ByteBuffer bb = ByteBuffer.wrap(b, 0, nbytes);

                // Not needed on desktop but required on android
                bb.order(ByteOrder.LITTLE_ENDIAN);

                short[] s = new short[nbytes/2];
                bb.asShortBuffer().get(s);
                d.processRaw(s, nbytes/2, false, false);
            }
        } catch (IOException e) {
            fail("Error when reading goforward.wav" + e.getMessage());
        }
        d.endUtt();
        System.out.println(d.hyp().getHypstr());
        for (Segment seg : d.seg()) {
            System.out.println(seg.getWord());
        }
    }

    private void runRecognizerSetup() {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(MainActivity.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {

                } else {
                }
            }
        }.execute();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runRecognizerSetup();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
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

    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {

    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                //.setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .setKeywordThreshold(1e-45f) // Threshold to tune for keyphrase to balance between false alarms and misses
                .setBoolean("-allphone_ci", true)  // Use context-independent phonetic search, context-dependent is too slow for mobile

                .getRecognizer();
        recognizer.addListener(this);


        /** In your application you might not need to add all those searches.
         * They are added here for demonstration. You can leave just one.
         */

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        // Create grammar-based search for selection between demos
        File menuGrammar = new File(assetsDir, "menu.gram");
        recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);

        // Create grammar-based search for digit recognition
        File digitsGrammar = new File(assetsDir, "digits.gram");
        recognizer.addGrammarSearch(DIGITS_SEARCH, digitsGrammar);

        // Create language model search
        File languageModel = new File(assetsDir, "weather.dmp");
        recognizer.addNgramSearch(FORECAST_SEARCH, languageModel);

        // Phonetic search
        File phoneticModel = new File(assetsDir, "en-phone.dmp");
        recognizer.addAllphoneSearch(PHONE_SEARCH, phoneticModel);
    }

    @Override
    public void onError(Exception error) {
    }

    @Override
    public void onTimeout() {

    }
}
