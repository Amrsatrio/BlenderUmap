/*
 * (C) 2020 amrsatrio. All rights reserved.
 */

package com.tb24.blenderumap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import kotlin.collections.CollectionsKt;
import kotlin.collections.MapsKt;
import kotlin.text.StringsKt;
import me.fungames.jfortniteparse.fileprovider.DefaultFileProvider;
import me.fungames.jfortniteparse.ue4.FGuid;
import me.fungames.jfortniteparse.ue4.assets.Package;
import me.fungames.jfortniteparse.ue4.assets.exports.UExport;
import me.fungames.jfortniteparse.ue4.assets.exports.UObject;
import me.fungames.jfortniteparse.ue4.assets.objects.FPackageIndex;
import me.fungames.jfortniteparse.ue4.assets.objects.FPropertyTag;
import me.fungames.jfortniteparse.ue4.assets.objects.FPropertyTagType;
import me.fungames.jfortniteparse.ue4.assets.objects.FRotator;
import me.fungames.jfortniteparse.ue4.assets.objects.FSoftObjectPath;
import me.fungames.jfortniteparse.ue4.assets.objects.FStructFallback;
import me.fungames.jfortniteparse.ue4.assets.objects.FVector;
import me.fungames.jfortniteparse.ue4.assets.objects.UScriptArray;
import me.fungames.jfortniteparse.ue4.pak.GameFile;
import me.fungames.jfortniteparse.ue4.versions.Ue4Version;

public class Main {
	private static File gamePath;
	private static Ue4Version gameVersion;
	private static String aes;
	private static boolean readMaterials;
	private static boolean runUmodel;
	private static boolean useGltf;
	private static Properties properties;
	private static File jsonsFolder = new File("jsons");
	private static Map<GameFile, Package> loaded = new HashMap<>();
	private static Set<String> toExport = new HashSet<>();
	private static Map<String, String[]> parsedMaterials = new HashMap<>();
	private static List<String> warnings = new ArrayList<>();
	private static long start = System.currentTimeMillis();

	public static void main(String[] args) {
		try {
			File configFile = new File("config.properties");

			if (!configFile.exists()) {
				System.err.println("config.properties not found");
				return;
			}

			properties = new Properties();
			properties.load(new FileInputStream(configFile));
			gamePath = new File(properties.getProperty("gamePath", "C:\\Program Files\\Epic Games\\Fortnite\\FortniteGame\\Content\\Paks"));
			gameVersion = Ue4Version.values()[Integer.parseInt(properties.getProperty("gameVersion", "2"))];
			aes = properties.getProperty("aes");
			readMaterials = Boolean.parseBoolean(properties.getProperty("readMaterials", "false"));
			runUmodel = Boolean.parseBoolean(properties.getProperty("runUmodel", "true"));
			useGltf = Boolean.parseBoolean(properties.getProperty("useGltf", "false"));
			String exportPackage = properties.getProperty("package");

			if (exportPackage == null || exportPackage.isEmpty()) {
				System.err.println("Please specify a package.");
				return;
			}

			if (aes == null || aes.isEmpty()) {
				System.out.println("No AES key provided. Please modify config.properties to include the AES key using \"aes=<hex>\".");
				System.out.println("Opening https://fnbot.shop/api/aes");
				Desktop.getDesktop().browse(new URI("https://fnbot.shop/api/aes"));
				return;
				// the solution below returns 403 for some reason
				/*System.out.println("AES is not defined, getting one from fnbot.shop...");

				try (Scanner scanner = new Scanner(new URL("https://fnbot.shop/api/aes").openStream(), "UTF-8").useDelimiter("\\A")) {
					if (scanner.hasNext()) {
						aes = scanner.next();
						System.out.println(aes);
					}
				}*/
			}

			jsonsFolder.mkdir();

			DefaultFileProvider provider = new DefaultFileProvider(gamePath, gameVersion);
			provider.submitKey(FGuid.Companion.getMainGuid(), aes);
			JsonArray bruh = exportAndProduceProcessed(provider, exportPackage);

			if (bruh == null) return;

			if (runUmodel && !toExport.isEmpty()) {
				exportUmodel();
			}

			if (readMaterials) {
				resolveMaterials(provider, bruh);
			}

			File file = new File("processed.json");
			System.out.println("Writing to " + file.getAbsolutePath());

			try (FileWriter writer = new FileWriter(file)) {
				JWPSerializer.GSON.toJson(bruh, writer);
			}

			try (FileWriter writer = new FileWriter(new File("summary.json"))) {
				JWPSerializer.GSON.toJson(warnings, writer);
			}

			System.out.println(String.format("All done in %,.1fs. In the Python script, replace the line with data_dir with this line below:\n", (System.currentTimeMillis() - start) / 1000.0F));
			System.out.println("data_dir = \"" + new File("").getAbsolutePath().replace("\\", "\\\\") + "\"");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Nullable
	private static JsonArray exportAndProduceProcessed(DefaultFileProvider provider, String s) throws IOException {
		System.out.println("\nExporting " + s);
		Package pkg = loadIfNot(provider, s);
		List<UExport> exports = pkg.getExports();
		File file = new File(jsonsFolder, s.substring(s.lastIndexOf('/') + 1, s.lastIndexOf('.')) + ".json");
		System.out.println("Writing to " + file.getAbsolutePath());

		try (FileWriter writer = new FileWriter(file)) {
			JWPSerializer.GSON.toJson(exports, writer);
		}

		if (!s.endsWith(".umap")) return null;

		JsonArray bruh = new JsonArray();

		for (UExport export : exports) {
			String exportType = export.getExportType();

			if (exportType.equals("LODActor")) {
				continue;
			}

			FPackageIndex smc = getProp(export, "StaticMeshComponent", FPackageIndex.class);

			if (smc == null) {
				continue;
			}

			UExport refSMC = exports.get(smc.getIndex() - 1);
			JsonArray obj = new JsonArray();
			bruh.add(obj);
			FGuid guid = getProp(export, "MyGuid", FGuid.class);
			obj.add(guid != null ? guidAsString(guid) : UUID.randomUUID().toString().replace("-", ""));
			obj.add(exportType);
			FPackageIndex mesh = getProp(refSMC, "StaticMesh", FPackageIndex.class);
			String meshS = null;
			FPackageIndex meshMat = null;

			if (mesh == null || mesh.getIndex() == 0) { // read the actor class to find the mesh
				GameFile actorPath = findBuildingActor(provider, exportType);

				if (actorPath != null) {
					Package actorPkg = loadIfNot(provider, actorPath);

					for (UExport actorExp : actorPkg.getExports()) {
						if (actorExp.getExportType().endsWith("StaticMeshComponent")) {
							mesh = getProp(actorExp, "StaticMesh", FPackageIndex.class);

							if (mesh != null && mesh.getIndex() != 0) {
								break;
							}
						}
					}
				}
			}

			if (mesh != null && mesh.getIndex() != 0) {
				meshS = mesh.getOuterImportObject().getObjectName().getText();
				/*String fixed = provider.fixPath(meshS);
				fixed = fixed.substring(0, fixed.lastIndexOf('.'));
				String finalFixed = fixed;
				List<GameFile> matches = new ArrayList<>(MapsKt.filter(provider.getFiles(), entry -> entry.getKey().startsWith(finalFixed)).values());
				matches.sort((o1, o2) -> o2.getPathWithoutExtension().length() - o1.getPathWithoutExtension().length());
				if (matches.size() > 1)
					System.err.println("More than 1 matches: " + Arrays.toString(matches.toArray()));
				meshS = matches.get(0).getPathWithoutExtension().replace("FortniteGame/", "/Game/");*/

				Package meshPkg = loadIfNot(provider, meshS = fix(exportType, meshS));

				if (meshPkg != null) {
					for (UExport meshExport : meshPkg.getExports()) {
						if (meshExport.getExportType().equals("StaticMesh")) {
							UScriptArray staticMaterials = getProp(meshExport, "StaticMaterials", UScriptArray.class);

							if (staticMaterials != null) {
								for (FPropertyTagType staticMaterial : staticMaterials.getContents()) {
									if ((meshMat = getProp(((FStructFallback) staticMaterial.getTagTypeValue()).getProperties(), "MaterialInterface", FPackageIndex.class)) != null) {
										break;
									}
								}
							}
						}

						if (meshMat != null) {
							break;
						}
					}

					toExport.add(meshS);
				}
			}

			FPackageIndex overrideMats = getProp(refSMC, "OverrideMaterials", FPackageIndex.class);
			String matToUse = overrideMats != null && overrideMats.getIndex() != 0 ? overrideMats.getOuterImportObject().getObjectName().getText() : meshMat != null && meshMat.getIndex() != 0 ? meshMat.getOuterImportObject().getObjectName().getText() : null;
//				String[] matTex = new String[4];

			if (matToUse != null) {
				/*Package matPkg = loadIfNot(provider, matToUse = fix(null, matToUse));
				UExport matFirstExp = matPkg.getExports().get(0);
				UScriptArray textureParameterValues = getProp(matFirstExp, "TextureParameterValues", UScriptArray.class);

				for (FPropertyTagType textureParameterValue : textureParameterValues.getContents()) {
					FStructFallback textureParameterValueS = (FStructFallback) textureParameterValue.getTagTypeValue();
					String name = getProp(getProp(textureParameterValueS.getProperties(), "ParameterInfo", FStructFallback.class).getProperties(), "Name", FName.class).getText();

					if (name != null && (name.equals("Diffuse") || name.equals("SpecularMasks") || name.equals("Normals"))) {
						String texPath = getProp(textureParameterValueS.getProperties(), "ParameterValue", FPackageIndex.class).getOuterImportObject().getObjectName().getText();

						if (name.equals("Diffuse")) {
							matTex[0] = texPath;
						} else if (name.equals("Normals")) {
							matTex[1] = texPath;
						} else if (name.equals("SpecularMasks")) {
							matTex[2] = texPath;
						} else if (name.equals("EmissiveTexture")) {
							matTex[3] = texPath;
						}

						toExport.add(texPath);*/
				toExport.add(matToUse);
//						}
//					}
			}

			JsonArray additional = new JsonArray();
			UScriptArray additionalWorlds = getProp(export, "AdditionalWorlds", UScriptArray.class);

			if (additionalWorlds != null) {
				for (FPropertyTagType additionalWorld : additionalWorlds.getContents()) {
					FSoftObjectPath additionalWorldS = (FSoftObjectPath) additionalWorld.getTagTypeValue();
					String text = additionalWorldS.getAssetPathName().getText();
					JsonArray result = exportAndProduceProcessed(provider, StringsKt.substringBeforeLast(text, '.', text) + ".umap");
					additional.add(result != null ? result : new JsonNull());
				}
			}

			FVector loc = getProp(refSMC, "RelativeLocation", FVector.class);
			FRotator rot = getProp(refSMC, "RelativeRotation", FRotator.class);
			FVector sc = getProp(refSMC, "RelativeScale3D", FVector.class);

			obj.add(mesh != null ? meshS : null);
			obj.add(matToUse);
			obj.add(JWPSerializer.GSON.toJsonTree(new String[4]));
			obj.add(loc != null ? vector(loc) : null);
			obj.add(rot != null ? rotator(rot) : null);
			obj.add(sc != null ? vector(sc) : null);
			obj.add(additional);
		}

		return bruh;
	}

	private static void resolveMaterials(DefaultFileProvider provider, JsonArray array) {
		for (JsonElement entry : array) {
			JsonArray entry1 = entry.getAsJsonArray();
			JsonElement mat = entry1.get(3);
			JsonElement children = entry1.get(8);

			if (mat.isJsonPrimitive()) {
				entry1.set(4, JWPSerializer.GSON.toJsonTree(MapsKt.getOrPut(parsedMaterials, mat.getAsString(), () -> {
					File matFile = new File(fix(null, mat.getAsString().substring(1)) + ".mat");
					String[] matTex = new String[4];

					try {
						for (String s1 : Files.readAllLines(matFile.toPath())) {
							String[] split = s1.split("=");

							if (split.length > 1) {
								String assign = split[1].toLowerCase() + ".ubulk";
								List<Map.Entry<String, GameFile>> filtered = provider.getFiles().entrySet().stream().filter(entry2 -> entry2.getKey().contains(assign)).collect(Collectors.toList());

								if (!filtered.isEmpty()) {
									String full = '/' + filtered.get(0).getValue().getPathWithoutExtension().replace("FortniteGame/Content", "Game");

									switch (split[0]) {
										case "Diffuse":
											matTex[0] = full;
											break;
										case "Normal":
											matTex[1] = full;
											break;
										case "Specular":
											matTex[2] = full;
											break;
										case "Emissive": // emissive broke
											// matTex[3] = full;
											break;
									}
								}
							}
						}
					} catch (IOException e) {
						// throw new RuntimeException("Failed when reading material", e);
						warn("Material failed to load: " + matFile);
					}

					return matTex;
				})));
			}

			if (children.isJsonArray()) {
				for (JsonElement childEntry : children.getAsJsonArray()) {
					resolveMaterials(provider, childEntry.getAsJsonArray());
				}
			}
		}
	}

	private static Package loadIfNot(DefaultFileProvider provider, String pkg) {
		GameFile gameFile = provider.findGameFile(pkg);

		if (gameFile != null) {
			return loadIfNot(provider, gameFile);
		} else {
			warn("Requested package " + pkg + " was not found");
			return null;
		}
	}

	private static Package loadIfNot(DefaultFileProvider provider, GameFile pkg) {
		return MapsKt.getOrPut(loaded, pkg, () -> provider.loadGameFile(pkg));
	}

	@NotNull
	private static String fix(String exportType, String fixS) {
		if (fixS.endsWith("WildWest_RockingChair")) {
			fixS += "_1";
		} else if (fixS.endsWith("Medium_Tree")) {
			fixS += "_2";
		} else if (fixS.endsWith("Treasure_Chest")) {
			fixS += "_2";
		} else if (fixS.endsWith("M_Rural_Garage")) {
			fixS += "_1";
		} else if ("Prop_WildWest_SimpleChair_01_C".equals(exportType)) {
			fixS += "_1";
		} else if ("Prop_WildWest_SimpleChair_02_C".equals(exportType)) {
			fixS += "_2";
		} else if ("Prop_WildWest_SimpleChair_03_C".equals(exportType)) {
			fixS += "_3";
		} else if ("Garage_Door_01_C".equals(exportType)) {
			fixS += "_1";
		} else if ("Apollo_Fac_Pipe_S_128_C".equals(exportType)) {
			fixS += "_128";
		} else if ("Apollo_Fac_Pipe_S_256_C".equals(exportType)) {
			fixS += "_256";
		} else if ("Apollo_Fac_Pipe_S_512_C".equals(exportType)) {
			fixS += "_512";
		} else if ("CornField_Rectangle_C".equals(exportType)) {
			fixS += "_2";
		}

		return fixS;
	}

	/*public static void main(String[] args) throws Exception {
		meshesSet.add("/Game/Packages/Fortress_Roofs/Meshes/SM_Fort_Roofs_Generic01");
		exportMeshes();
	}*/

	private static void exportUmodel() throws InterruptedException, IOException {
		for (List<String> chunk : CollectionsKt.chunked(toExport, 250)) {
			List<String> parts = new ArrayList<>();
			parts.add("umodel");
			h(parts, "-path=\"" + gamePath + '\"');
			parts.add("-game=ue" + (gameVersion == Ue4Version.GAME_UE4_22 ? "4.22" : gameVersion == Ue4Version.GAME_UE4_23 ? "4.23" : gameVersion == Ue4Version.GAME_UE4_24 ? "4.24" : gameVersion == Ue4Version.GAME_UE4_25 ? "4.25" : /* Fortnite 12.61 */ "4.24"));
			parts.add("-aes=" + (aes.startsWith("0x") ? aes : "0x" + aes));
			h(parts, "-out=\"" + new File("").getAbsolutePath() + '\"');

			if (useGltf) {
				parts.add("-gltf");
			}

			boolean bFirst = true;

			for (String export : chunk) {
				if (bFirst) {
					bFirst = false;
					parts.add("-export");
					parts.add(export);
				} else {
					parts.add("-pkg=" + export);
				}
			}

			System.out.println("Invoking UModel: " + CollectionsKt.joinToString(parts, " ", "", "", -1, "...", null));
			ProcessBuilder pb = new ProcessBuilder(parts);
			pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
			pb.redirectError(ProcessBuilder.Redirect.INHERIT);
			pb.start().waitFor();
		}
	}

	private static void h(List<String> command, String s) {
		Collections.addAll(command, s.split(" "));
	}

	private static GameFile findBuildingActor(DefaultFileProvider provider, String actorName) {
		if (actorName.endsWith("_C")) {
			actorName = actorName.substring(0, actorName.length() - 2);
		}

		String check = '/' + actorName.toLowerCase() + ".uasset";
		List<Map.Entry<String, GameFile>> filtered = provider.getFiles().entrySet().stream().filter(entry -> entry.getKey().endsWith(check)).collect(Collectors.toList());

		if (filtered.size() == 1) {
			return filtered.get(0).getValue();
		}

		filtered = filtered.stream().filter(entry -> entry.getKey().contains("Actor".toLowerCase())).collect(Collectors.toList()); // keys are lower cased

		if (!filtered.isEmpty()) {
			GameFile out = filtered.get(0).getValue();

			if (filtered.size() > 1) {
				warn(actorName + ": Found " + filtered.size() + " actors. Picked the first one: " + out);
			}

			return out;
		}

		warn("Actor not found: " + actorName);
		return null;
	}

	private static <T> T getProp(List<FPropertyTag> properties, String name, Class<T> clazz) {
		for (FPropertyTag prop : properties) {
			if (name.equals(prop.getName().getText())) {
				Object tagTypeValue = prop.getTagTypeValue();
				return clazz.isInstance(tagTypeValue) ? (T) tagTypeValue : null;
			}
		}

		return null;
	}

	private static <T> T getProp(UExport export, String name, Class<T> clazz) {
		if (export instanceof UObject) {
			return getProp(((UObject) export).getProperties(), name, clazz);
		} else {
			System.out.println("Skipping " + export.getExportType());
			return null;
		}
	}

	private static String guidAsString(FGuid guid) {
		return String.format("%08x%08x%08x%08x", guid.getPart1(), guid.getPart2(), guid.getPart3(), guid.getPart4());
	}

	private static JsonArray vector(FVector vector) {
		JsonArray array = new JsonArray(3);
		array.add(vector.getX());
		array.add(vector.getY());
		array.add(vector.getZ());
		return array;
	}

	private static JsonArray rotator(FRotator rotator) {
		JsonArray array = new JsonArray(3);
		array.add(rotator.getPitch());
		array.add(rotator.getYaw());
		array.add(rotator.getRoll());
		return array;
	}

	public static void warn(String message) {
		System.out.println("WARNING: " + message);
		warnings.add(message);
	}
}
