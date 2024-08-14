package kz.awsstudio.pixai.client;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Client for interacting with the PixAI API.
 * This class provides methods to generate images based on a given prompt
 * and to download the generated images.
 */
public class PixAIClient {

    private String apiKey;
    private JSONObject photoConfig = new JSONObject();
    private OkHttpClient client = new OkHttpClient();
    private String saveFilePath = "";

    /**
     * Constructor to initialize PixAIClient with the provided API key.
     * 
     * @param apiKey API key for accessing the PixAI API.
     */
    public PixAIClient(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Starts the image generation process based on the given prompt.
     * 
     * @param prompt The textual description for generating the image.
     * @throws IOException If an error occurs during the request.
     */
    public void run(String prompt) throws IOException {
        String taskId = createGenerationTask(prompt);
        String status = pollTaskStatus(taskId);
        System.out.println("Task status: " + status);

        if ("completed".equals(status)) {
            String mediaId = getMediaId(taskId);
            downloadGeneratedImage(mediaId);
        }
    }

    /**
     * Creates a task to generate an image using the PixAI API.
     * 
     * @param prompt The textual description for generating the image.
     * @return The ID of the generation task.
     * @throws IOException If an error occurs during the request.
     */
    private String createGenerationTask(String prompt) throws IOException {
        String graphqlQuery = "mutation createGenerationTask($parameters: JSONObject!) { createGenerationTask(parameters: $parameters) { id } }";

        JSONObject variables = new JSONObject();
        JSONObject parameters = new JSONObject(photoConfig.toString());
        parameters.put("prompts", prompt);
        variables.put("parameters", parameters);

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("query", graphqlQuery);
        jsonBody.put("variables", variables);

        Request request = sendRequest(jsonBody);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response + " with message: " + response.body().string());
            }

            String responseBodyString = response.body().string();
            JSONObject responseBody = new JSONObject(responseBodyString);
            System.out.println("Start Generation...");
            return responseBody.getJSONObject("data").getJSONObject("createGenerationTask").getString("id");
        }
    }

    /**
     * Downloads the generated image using the mediaId.
     * 
     * @param mediaId The media ID of the image to download.
     * @throws IOException If an error occurs during the request.
     */
    private void downloadGeneratedImage(String mediaId) throws IOException {
        String graphqlQuery = "query getMediaById($id: String!) { media(id: $id) { urls { variant url } } }";

        JSONObject variables = new JSONObject();
        variables.put("id", mediaId);

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("query", graphqlQuery);
        jsonBody.put("variables", variables);

        Request request = sendRequest(jsonBody);

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response + " with message: " + response.body().string());
        }

        String responseBodyString = response.body().string();
        JSONObject responseBody = new JSONObject(responseBodyString);

        JSONArray urls = responseBody.getJSONObject("data").getJSONObject("media").getJSONArray("urls");
        String downloadUrl = null;

        for (int i = 0; i < urls.length(); i++) {
            JSONObject urlObj = urls.getJSONObject(i);
            if (urlObj.has("url")) {
                downloadUrl = urlObj.getString("url");
                break;
            }
        }

        downloadFile(downloadUrl);
    }

    /**
     * Sends a GraphQL request to the PixAI API.
     * 
     * @param jsonBody The JSON body of the request.
     * @return The constructed HTTP request.
     */
    private Request sendRequest(JSONObject jsonBody) {
        RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                jsonBody.toString()
        );

        return new Request.Builder()
                .url("https://api.pixai.art/graphql")
                .post(requestBody)
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /**
     * Polls the status of the image generation task.
     * 
     * @param taskId The ID of the generation task.
     * @return The status of the task ("completed", "failed", "cancelled").
     * @throws IOException If an error occurs during the request.
     */
    private String pollTaskStatus(String taskId) throws IOException {
        String graphqlQuery = "query getTaskById($id: ID!) { task(id: $id) { id status } }";

        JSONObject variables = new JSONObject();
        variables.put("id", taskId);

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("query", graphqlQuery);
        jsonBody.put("variables", variables);

        while (true) {
            Request request = new Request.Builder()
                    .url("https://api.pixai.art/graphql")
                    .post(RequestBody.create(MediaType.parse("application/json"), jsonBody.toString()))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response + " with message: " + response.body().string());
            }

            JSONObject responseBody = new JSONObject(response.body().string());
            String status = responseBody.getJSONObject("data").getJSONObject("task").getString("status");

            if ("completed".equals(status) || "failed".equals(status) || "cancelled".equals(status)) {
                return status;
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
        }
    }

    /**
     * Retrieves the media ID (mediaId) from the API response for the generation task.
     * 
     * @param taskId The ID of the generation task.
     * @return The media ID (mediaId).
     * @throws IOException If an error occurs during the request.
     */
    private String getMediaId(String taskId) throws IOException {
        String graphqlQuery = "query getTaskById($id: ID!) { task(id: $id) { outputs } }";

        JSONObject variables = new JSONObject();
        variables.put("id", taskId);

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("query", graphqlQuery);
        jsonBody.put("variables", variables);

        Request request = sendRequest(jsonBody);

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response + " with message: " + response.body().string());
        }

        String responseBodyString = response.body().string();
        JSONObject responseBody = new JSONObject(responseBodyString);

        JSONObject outputs = responseBody.getJSONObject("data").getJSONObject("task").getJSONObject("outputs");
        String mediaId = outputs.optString("mediaId", null);
        if (mediaId == null) {
            throw new JSONException("JSONObject['mediaId'] not found.");
        }
        return mediaId;
    }

    /**
     * Downloads a file from the specified URL and saves it to the local disk.
     * 
     * @param downloadUrl The URL to download the file from.
     * @throws IOException If an error occurs during the request or saving the file.
     */
    private void downloadFile(String downloadUrl) throws IOException {
        Request request = new Request.Builder()
                .url(downloadUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response + " with message: " + response.body().string());
        }

        byte[] imageBytes = response.body().bytes();

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "picture_PixAI_" + timeStamp + ".png";
        Files.createDirectories(Paths.get(saveFilePath));

        try (FileOutputStream fos = new FileOutputStream(Paths.get(saveFilePath, fileName).toString())) {
            fos.write(imageBytes);
            System.out.println("Image downloaded: " + saveFilePath + "/" + fileName);
        }
    }

    /**
     * Sets the file path for saving downloaded images.
     * 
     * @param saveFilePath The path to save downloaded files.
     */
    public void setOutputFilePath(String saveFilePath) {
        this.saveFilePath = saveFilePath;
    }

    /**
     * Sets the negative prompt for image generation.
     * 
     * @param newNegativePrompt The text for the negative prompt.
     */
    public void setNegativePrompt(String newNegativePrompt) {
        photoConfig.put("negative_prompt", newNegativePrompt);
    }

    /**
     * Enables or disables the Tile option in the image configuration.
     * 
     * @param enableTile Enable or disable Tile.
     */
    public void setEnableTile(boolean enableTile) {
        photoConfig.put("enableTile", enableTile);
    }

    /**
     * Sets the number of sampling steps for image generation.
     * 
     * @param samplingSteps The number of sampling steps.
     */
    public void setSamplingSteps(int samplingSteps) {
        photoConfig.put("samplingSteps", samplingSteps);
    }

    /**
     * Sets the CFG Scale for image generation.
     * 
     * @param cfgScale The CFG Scale value.
     */
    public void setCfgScale(double cfgScale) {
        photoConfig.put("cfgScale", cfgScale);
    }

    /**
     * Sets the upscale factor for image generation.
     * 
     * @param upscale The upscale factor value.
     */
    public void setUpscale(double upscale) {
        photoConfig.put("upscale", upscale);
    }

    /**
     * Sets the width of the image in pixels.
     * 
     * @param width The width of the image.
     */
    public void setWidth(int width) {
        photoConfig.put("width", width);
    }

    /**
     * Sets the height of the image in pixels.
     * 
     * @param height The height of the image.
     */
    public void setHeight(int height) {
        photoConfig.put("height", height);
    }

    /**
     * Sets the sampler type for image generation.
     * 
     * @param sampler The sampler type.
     */
    public void setSampler(String sampler) {
        photoConfig.put("sampler", sampler);
    }

    /**
     * Sets the model ID for image generation.
     * 
     * @param modelId The model ID.
     */
    public void setModelId(String modelId) {
        photoConfig.put("modelId", modelId);
    }
}
