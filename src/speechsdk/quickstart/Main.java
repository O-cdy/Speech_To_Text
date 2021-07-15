package speechsdk.quickstart;


import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.io.*;

import com.google.gson.*;
import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.translation.*;
import com.squareup.okhttp.*;

public class Main {
    public static Main translateRequest = new Main();
    private static String response, transcript = "";
    final String subscriptionKey = "YourSubscriptionKey";
    final String location = "global";

    static FileInputStream fin;
    static InputStreamReader isr;
    static BufferedReader br;
    static FileOutputStream fos;
    static PrintWriter pw;
    static StringBuffer sb;

    HttpUrl url = new HttpUrl.Builder()
            .scheme("https")
            .host("api.cognitive.microsofttranslator.com")
            .addPathSegment("/translate")
            .addQueryParameter("api-version", "3.0")
            .addQueryParameter("from", "en")
            .addQueryParameter("to", "zh-Hans")
            .build();

    // Instantiates the OkHttpClient.
    OkHttpClient client = new OkHttpClient();


    // This function performs a POST request.
    public String Post(String s) throws IOException {
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType,s);
        Request request = new Request.Builder().url(url).post(body)
                .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
                .addHeader("Ocp-Apim-Subscription-Region", location)
                .addHeader("Content-type", "application/json")
                .build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    // This function prettifies the json response.
    public static String prettify(String json_text) {
        JsonParser parser = new JsonParser();
        JsonElement json = parser.parse(json_text);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(json);
    }

    private static Semaphore stopTranslationWithFileSemaphore;
    static SpeechTranslationConfig speechConfig = SpeechTranslationConfig.fromSubscription("YourSubscriptionKey", "eastasia");

    // This function translate the speech from the microphone to text
    public static void translationWithMicrophoneAsync() throws Exception {
        String fromLanguage = "en-US";
        speechConfig.setSpeechRecognitionLanguage("en-US");
        speechConfig.addTargetLanguage("zh-CN");

        // Creates a translation recognizer using microphone as audio input.
        TranslationRecognizer recognizer = new TranslationRecognizer(speechConfig);
        {
            // Subscribes to events.
            recognizer.recognizing.addEventListener((s, e) -> {
                System.out.println("RECOGNIZING in '" + fromLanguage + "': Text=" + e.getResult().getText());

                Map<String, String> map = e.getResult().getTranslations();
                for(String element : map.keySet()) {
                    System.out.println("    TRANSLATING into '" + element + "': " + map.get(element));
                }
            });

            recognizer.recognized.addEventListener((s, e) -> {
                if (e.getResult().getReason() == ResultReason.TranslatedSpeech) {
                    System.out.println("RECOGNIZED in '" + fromLanguage + "': Text=" + e.getResult().getText());

                    Map<String, String> map = e.getResult().getTranslations();
                    for(String element : map.keySet()) {
                        System.out.println("    TRANSLATED into '" + element + "': " + map.get(element));
                    }
                }
                if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                    System.out.println("RECOGNIZED: Text=" + e.getResult().getText());
                    System.out.println("    Speech not translated.");
                }
                else if (e.getResult().getReason() == ResultReason.NoMatch) {
                    System.out.println("NOMATCH: Speech could not be recognized.");
                }
            });

            recognizer.synthesizing.addEventListener((s, e) -> {
                System.out.println("Synthesis result received. Size of audio data: " + e.getResult().getAudio().length);
            });

            recognizer.canceled.addEventListener((s, e) -> {
                System.out.println("CANCELED: Reason=" + e.getReason());

                if (e.getReason() == CancellationReason.Error) {
                    System.out.println("CANCELED: ErrorCode=" + e.getErrorCode());
                    System.out.println("CANCELED: ErrorDetails=" + e.getErrorDetails());
                    System.out.println("CANCELED: Did you update the subscription info?");
                }
            });

            recognizer.sessionStarted.addEventListener((s, e) -> {
                System.out.println("\nSession started event.");
            });

            recognizer.sessionStopped.addEventListener((s, e) -> {
                System.out.println("\nSession stopped event.");
            });

            // Starts continuous recognition. Uses StopContinuousRecognitionAsync() to stop recognition.
            System.out.println("Say something...");
            recognizer.startContinuousRecognitionAsync().get();

            System.out.println("Press any key to stop");
            new Scanner(System.in).nextLine();

            recognizer.stopContinuousRecognitionAsync().get();
        }
    }

    // This function translate the speech from the WAV file to text
    private static void translationWithFileAsync(String fileName) throws Exception {
        String fromLanguage = "en-US"; // zh-CN
        String toLanguage = "zh-CN";
//        String[] toLanguages = { "it", "fr", "de" };
        speechConfig.setSpeechRecognitionLanguage(fromLanguage);
        speechConfig.addTargetLanguage(toLanguage);
//        for (String language : toLanguages) {
//            speechConfig.addTargetLanguage(language);
//        }

        AudioConfig audioConfig = AudioConfig.fromWavFileInput("D:\\microsoft\\resources\\"+ fileName +".wav");
        SpeechRecognizer recognizer = new SpeechRecognizer(speechConfig, audioConfig);

//        First initialize the semaphore.
        stopTranslationWithFileSemaphore = new Semaphore(0);

        recognizer.recognizing.addEventListener((s, e) -> {
//            System.out.println("RECOGNIZING: Text=" + e.getResult().getText());
        });

        recognizer.recognized.addEventListener((s, e) -> {
            if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                String x = e.getResult().getText();
                System.out.println("RECOGNIZED: Text=" + e.getResult().getText());

                try {
                    response = translateRequest.Post("[{\"Text\": \""+e.getResult().getText()+"\"}]");
//                    System.out.println(prettify(response));
                    System.out.println(response);
                    transcript += x + "\n" + response + "\n\n";
                } catch (Exception Exception) {
                    Exception.printStackTrace();
                }

            }
            else if (e.getResult().getReason() == ResultReason.NoMatch) {
                System.out.println("NOMATCH: Speech could not be recognized.");
            }
        });

        recognizer.canceled.addEventListener((s, e) -> {
            System.out.println("CANCELED: Reason=" + e.getReason());

            if (e.getReason() == CancellationReason.Error) {
                System.out.println("CANCELED: ErrorCode=" + e.getErrorCode());
                System.out.println("CANCELED: ErrorDetails=" + e.getErrorDetails());
                System.out.println("CANCELED: Did you update the subscription info?");
            }

            stopTranslationWithFileSemaphore.release();
        });

        recognizer.sessionStopped.addEventListener((s, e) -> {
            System.out.println("\n    Session stopped event.");
            stopTranslationWithFileSemaphore.release();
        });

        // Starts continuous recognition. Uses StopContinuousRecognitionAsync() to stop recognition.
        recognizer.startContinuousRecognitionAsync().get();

        // Waits for completion.
        stopTranslationWithFileSemaphore.acquire();

        // Stops recognition.
        recognizer.stopContinuousRecognitionAsync().get();
        initialFile(fileName);
        write(transcript);
    }

    // This function convert file's type to WAV type (MOV , mp4 -> WAV)
    public static String convertFormat(String fileName,String type) {
        String webroot = "D:\\soft\\ffmpeg\\bin";  //ffmpeg安装路径

        String sourcePath = "D:\\microsoft\\resources\\" + fileName + "." + type;
//        String sourcePath = "D:\\" + fileName + ".mov";
        String targetPath = "D:\\microsoft\\resources\\" + fileName + ".wav";
        Runtime run = null;
        try

        {
            run = Runtime.getRuntime();
            long start = System.currentTimeMillis();
//            System.out.println(new File(webroot).getAbsolutePath());
            System.out.println("format converting...");

            //执行ffmpeg.exe,前面是ffmpeg.exe的地址，中间是需要转换的文件地址，后面是转换后的文件地址。-i是转换方式，意思是可编码解码，mp3编码方式采用的是libmp3lame
            Process p = run.exec(new File(webroot).getAbsolutePath() + "/ffmpeg -y -i " + sourcePath + " -acodec pcm_s16le -ac 2 -ar 8000 " + targetPath);
            p.getOutputStream().close();
            p.getInputStream().close();
            p.getErrorStream().close();
            p.waitFor();
            long end = System.currentTimeMillis();

            System.out.println(sourcePath + " convert success, costs:" + (end - start) + "ms , to " + targetPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "1";
    }

    public static void initialFile(String filename) throws Exception {
        File file = new File("D:/microsoft/transcript/" + filename + ".txt");

        if(file.exists()){
            if (file.delete()) {
                if(file.createNewFile()){
                    System.out.println(filename + ".txt create successfully! ");
                } else {
                    System.out.println(filename + ".txt create fail! ");
                }
            }
        } else {
            if(file.createNewFile()){
                System.out.println(filename + ".txt create successfully! ");
            } else {
                System.out.println(filename + ".txt create fail! ");
            }
        }


        fin = new FileInputStream("D:/microsoft/transcript/"+filename+".txt");
        isr = new InputStreamReader(fin);
        br = new BufferedReader(isr);

        fos = new FileOutputStream("D:/microsoft/transcript/"+filename+".txt");
        pw = new PrintWriter(fos);

        sb = new StringBuffer();
    }

    // Write down the text into result file
    public static void write(String filein) throws Exception {

        while(br.readLine() != null){
            sb.append(br.readLine());
        }
        sb.append(filein);

        pw.write(sb.toString().toCharArray());
        pw.flush();

        System.out.println("write successfully!");

        fin.close();
        isr.close();
        br.close();
        fos.close();
        pw.close();

    }


    public static void main(String[] args) {
        try {
            
//            translationWithMicrophoneAsync();

//            convertFormat("WeatherAppVideo","MOV");
//            convertFormat("监视资本主义","mp4");
//			convertFormat("监视资本主义_预告","mp4");

//            translationWithFileAsync("WeatherAppVideo");
//            translationWithFileAsync("监视资本主义");
			translationWithFileAsync("监视资本主义");

        } catch (Exception ex) {
            System.exit(1);
        }
    }

}
