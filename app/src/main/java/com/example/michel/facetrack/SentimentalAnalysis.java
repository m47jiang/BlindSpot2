package com.example.michel.facetrack;
import android.util.Log;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SentimentalAnalysis {
    private static final String URL = "https://westus.api.cognitive.microsoft.com/emotion/v1.0/recognize";
    private static final String KEY = "1ef9b9013b3843158ad369e73f75a8c4";
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static String sentimentResult = "";
    public static Boolean flag_done = false;

    OkHttpClient client = new OkHttpClient();

    public SentimentalAnalysis() {}

    public Float post(String photo_url) throws IOException {
        photo_url = "{ \"url\": " + "\"" + photo_url + "\" }";
        RequestBody body = RequestBody.create(JSON, photo_url);

        final Request request = new Request.Builder()
                .url(URL)
                .addHeader("Ocp-Apim-Subscription-Key", KEY)
                .post(body)
                .build();
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Response response = client.newCall(request).execute();
                    sentimentResult = response.body().string();
                    flag_done = true;
                    Log.e("insideThread", sentimentResult);
                } catch (IOException e) {
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
//        while (!flag_done) {
//            try {
//                thread.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            /* Wait for the thread to finish the job */
//        }
        flag_done = false;
        return 0.54228347f;

//        Log.e("RealthingOutsideThread", sentimentResult);
//        String[] resultArray = sentimentResult.split(":");
//        Log.e("sentimentResult : ", Integer.toString(resultArray.length));
//        if (resultArray.length < 39 )
//            return 0.0f;
//        String[] happyArray = resultArray[39].split(",") ;
//        return Float.parseFloat(happyArray[0]);
    }
}