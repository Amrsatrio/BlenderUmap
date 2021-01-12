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
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

import kotlin.io.FilesKt;
import me.fungames.jfortniteparse.util.DataTypeConverterKt;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class FetchFortniteAesKey {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final OkHttpClient okHttpClient = new OkHttpClient();

	public static void main(String[] args) {
		try {
			updateEncryptionKeys();
			updateMappings();
			System.out.println("Done");
			System.exit(0);
		} catch (GeneralSecurityException | IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void updateEncryptionKeys() throws IOException {
		System.out.println("Fetching encryption keys...");
		Reader reader = okHttpClient.newCall(new Request.Builder().url("https://benbotfn.tk/api/v1/aes").build()).execute().body().charStream();
		System.out.println("Updating config...");
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
	}

	private static void updateMappings() throws IOException, GeneralSecurityException {
		System.out.println("Fetching available mappings...");
		Reader reader = okHttpClient.newCall(new Request.Builder().url("https://benbotfn.tk/api/v1/mappings").build()).execute().body().charStream();
		FileEntry[] response = GSON.fromJson(reader, FileEntry[].class);
		FileEntry chosen = null;
		for (FileEntry entry : response) {
			if ("Oodle".equalsIgnoreCase(entry.meta.get("compressionMethod"))) {
				chosen = entry;
				break;
			}
		}
		if (chosen == null) {
			System.out.println("No mappings found. Please supply your own Oodle compressed .usmap mappings in mappings folder.");
			return;
		}
		File mappingsFolder = new File("mappings");
		File mappingsFile = new File(mappingsFolder, chosen.fileName);
		if (mappingsFile.exists()) {
			MessageDigest sha1 = MessageDigest.getInstance("SHA1");
			sha1.update(FilesKt.readBytes(mappingsFile));
			if (Arrays.equals(sha1.digest(), DataTypeConverterKt.parseHexBinary(chosen.hash))) {
				System.out.println("Mappings already up to date.");
				return;
			} else {
				System.out.println("Integrity check failed.");
			}
		}
		System.out.println("Downloading latest mappings...");
		byte[] usmapData = okHttpClient.newCall(new Request.Builder().url(chosen.url).build()).execute().body().bytes();
		System.out.println("Saving mappings to " + mappingsFile.getAbsolutePath() + "...");
		if (!mappingsFolder.exists()) {
			mappingsFolder.mkdir();
		}
		FilesKt.writeBytes(mappingsFile, usmapData);
	}

	public static class AesResponse {
		public String version;
		public String mainKey;
		public Map<String, String> dynamicKeys;
	}

	public static class FileEntry {
		public String url;
		public String fileName;
		public String hash;
		public Long length;
		public Date uploaded;
		public Map<String, String> meta;
	}
}
