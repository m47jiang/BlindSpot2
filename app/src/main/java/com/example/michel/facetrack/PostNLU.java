package com.example.michel.facetrack;

import android.util.Log;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by michel on 22/01/17.
 */
public class PostNLU {
    private static final String URL = "https://webapi-demo.nuance.mobi:11443/nina-webapi/";
    private static final String nmaid = "Nuance_ConUHack2017_20170119_210049";
    private static final String nmaidkey = "0d11e9c5b897eefdc7e0aad840bf4316a44ea91f0d76a2b053be294ce95c7439dee8c3a6453cf7db31a12e08555b266d54c2300470e4140a4ea4c8ba285962fd";
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static String nluResult = "";
    public static Boolean flag_done = false;

    static final OkHttpClient client = new OkHttpClient();

    public static Intention post(String speechText) throws IOException {
        String bodyText = "{ \"text\": " + "\"" + speechText + "\"," +
                "\"nlu_engine\": \"NR\"," +
                "\"nlu_engine_parameters\": \n" +
                "{\n" +
                "\n" +
                "    \"nleModelURL\": \"string\",\n" +
                "    \"nleModelName\": \"string\"\n" +
                "\n" +
                "}," +
                "\"companyName\": \"HackGShenCo\",\n" +
                "\"appName\": \"BlindSpot\",\n" +
                "\"cloudModelVersion\": \"1.0.0\",\n" +
                "\"user\": \"1\" }";
        RequestBody body = RequestBody.create(JSON, bodyText);

        final Request request = new Request.Builder()
                .url(URL)
                .addHeader("nmaid", nmaid)
                .addHeader("nmaidkey", nmaidkey)
                .post(body)
                .build();
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Response response = client.newCall(request).execute();
                    nluResult = response.body().string();
                    flag_done = true;
                    Log.e("insideThreadNLU", nluResult);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
        while (!flag_done) { /* Wait for the thread to finish the job */ }
        flag_done = false;
        Log.e("OutsideThreadNLU", nluResult);
        String[] resultArray = nluResult.split(":");
        return new Intention(Intent.TAKE, PhotoType.SELFIE);
    }

    public static class Intention {

        public Intent intent;
        public PhotoType photoType;

        public Intention (Intent _intent, PhotoType _photoType) {
            this.intent = _intent;
            this.photoType = _photoType;
        }
    }

    public enum Intent {
        TAKE, OPEN
    }

    public enum PhotoType {
        PHOTO, SELFIE
    }
}
