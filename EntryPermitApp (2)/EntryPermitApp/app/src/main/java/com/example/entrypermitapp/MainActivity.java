package com.example.entrypermitapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private final static int SAMPLE_RATE= 8000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            CHANNEL_CONFIG, AUDIO_FORMAT);

    private final int REFERENCE_AMPLITUDE = 32767;


    private TextInputEditText main_EDT_password;
    private MaterialButton main_BTN_login;

    private SensorManager sensorManager;
    private Sensor magneticSensor;
    private Sensor accelerometerSensor;
    private float[] magneticValues = new float[3];
    private float[] accelerometerValues = new float[3];
    private int batteryLevel;
    private float northOrientation;
    private AudioManager audioManager;
    private static final int NOISY_SOUND_LEVEL = 20;
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 1;
    private MediaRecorder mediaRecorder;
    private boolean isTestingNoise = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViews();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        main_BTN_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onLoginButtonClick(v);
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    private void findViews() {
        main_EDT_password = findViewById(R.id.main_EDT_password);
        main_BTN_login = findViewById(R.id.main_BTN_login);
    }

    @Override
    protected void onResume() {
      super.onResume();
      sensorManager.registerListener((SensorEventListener) this, magneticSensor, SensorManager.SENSOR_DELAY_NORMAL);
      sensorManager.registerListener((SensorEventListener) this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
      batteryLevel = getBatteryLevel();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener((SensorEventListener) this, magneticSensor);
        sensorManager.unregisterListener((SensorEventListener) this, accelerometerSensor);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticValues = event.values.clone();
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerValues = event.values.clone();
        }

        float[] rotationMatrix = new float[9];
        boolean success = SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerValues, magneticValues);
        if (success) {
            float[] orientationValues = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientationValues);
            float azimuth = (float) Math.toDegrees(orientationValues[0]);
            if (azimuth < 0) {
                azimuth += 360;
            }
            northOrientation = Math.round(azimuth);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing
    }

    private int getBatteryLevel() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return Math.round((float) level / (float) scale * 100.0f);
    }

    private void onLoginButtonClick(View view) {
        String password = main_EDT_password.getText().toString();
        int roundedNorthOrientation = Math.round(northOrientation);
        int ringerMode = audioManager.getRingerMode();
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);

        if (password.equals(String.valueOf(batteryLevel)) &&
                (roundedNorthOrientation >= 0 && roundedNorthOrientation <= 45 ||
                        roundedNorthOrientation >= 315 && roundedNorthOrientation <= 360 &&
                                currentVolume >= NOISY_SOUND_LEVEL)) {
            // Password matches battery level and north orientation, check for noisy environment
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, check for noisy environment
                int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
                AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
                short[] audioBuffer = new short[bufferSize];
                audioRecord.startRecording();
                int numSamplesRead = audioRecord.read(audioBuffer, 0, bufferSize);
                audioRecord.stop();
                audioRecord.release();

                double averageAmplitude = 0.0;
                for (int i = 0; i < numSamplesRead; i++) {
                    averageAmplitude += Math.abs(audioBuffer[i]);
                }
                averageAmplitude /= numSamplesRead;
                double db = 20 * Math.log10(averageAmplitude / REFERENCE_AMPLITUDE);

                if (db > NOISY_SOUND_LEVEL) {
                    // Environment is noisy, show error message
                    Toast.makeText(this, "Login Failed: Noisy Environment", Toast.LENGTH_SHORT).show();
                } else {
                    // Environment is not noisy, log in
                    Intent intent = new Intent(MainActivity.this, EntryPermitActivity.class);
                    startActivity(intent);
                    Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Permission not granted, request permission
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO}, 1);
            }
        } else {
            // Password is incorrect, show error message
            Toast.makeText(this, "Login Failed", Toast.LENGTH_SHORT).show();
        }
    }

    private int getNoiseLevel() {
        int noiseLevel = 0;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Microphone permission has been granted
            try {
                // Set up the audio recorder
                AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
                audioRecord.startRecording();
                // Read the audio data into a buffer
                short[] buffer = new short[BUFFER_SIZE];
                int read = audioRecord.read(buffer, 0, BUFFER_SIZE);
                // Calculate the average amplitude of the audio data
                double sum = 0;
                for (int i = 0; i < read; i++) {
                    sum += buffer[i] * buffer[i];
                }
                double amplitude = Math.sqrt(sum / read);
                noiseLevel = (int) (20 * Math.log10(amplitude / REFERENCE_AMPLITUDE));
                // Stop the audio recorder
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Microphone permission has not been granted, return 0
            noiseLevel = 0;
        }
        return noiseLevel;
    }
    private boolean isEnvironmentNoisy() {
        boolean isNoisy = false;
        int noiseLevel = getNoiseLevel();
        if(noiseLevel >= NOISY_SOUND_LEVEL) {
            isNoisy = true;
        }
        return isNoisy;
    }
}