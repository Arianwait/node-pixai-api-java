package kz.awsstudio.pixai.example;

import java.io.IOException;

import kz.awsstudio.pixai.client.PixAIClient;

public class PixAIExample {
	public static void main(String[] args) {
        PixAIClient client = new PixAIClient("your-api-key");
        
        client.setNegativePrompt("better quality, sharp details");
        client.setSamplingSteps(50);
        client.setOutputFilePath("set-path");
        client.setModelId("1648918127446573124");
        client.setWidth(768);
        client.setHeight(1280);
        try {
			client.run("your-print");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
}
