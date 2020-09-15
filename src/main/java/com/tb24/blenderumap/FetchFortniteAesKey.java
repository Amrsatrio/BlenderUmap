package com.tb24.blenderumap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class FetchFortniteAesKey {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static void main(String[] args) {
		try {
			Reader reader = new OkHttpClient().newCall(new Request.Builder().url("https://benbotfn.tk/api/v1/aes").build()).execute().body().charStream();
			System.out.println("Fetched, writing...");
			AesResponse response = GSON.fromJson(reader, AesResponse.class);
			reader.close();
			File configFile = new File("config.json");
			FileReader fileReader = new FileReader(configFile);
			JsonElement configTree = JsonParser.parseReader(fileReader);
			fileReader.close();
			JsonArray keys = configTree.getAsJsonObject().getAsJsonArray("EncryptionKeys");

			while (keys.size() > 0) {
				keys.remove(0);
			}

			JsonObject mainKey = new JsonObject();
			mainKey.addProperty("Guid", "00000000000000000000000000000000");
			mainKey.addProperty("Key", response.mainKey);
			keys.add(mainKey);

			for (Map.Entry<String, String> entry : response.dynamicKeys.entrySet()) {
				JsonObject dynKey = new JsonObject();
				dynKey.addProperty("FileName", entry.getKey().substring(entry.getKey().lastIndexOf('/') + 1));
				dynKey.addProperty("Key", entry.getValue());
				keys.add(dynKey);
			}

			FileWriter fileWriter = new FileWriter(configFile);
			GSON.toJson(configTree, fileWriter);
			fileWriter.close();
			System.out.println("Done");
			System.exit(0);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static class AesResponse {
		public String version;
		public String mainKey;
		public Map<String, String> dynamicKeys;
	}
}
