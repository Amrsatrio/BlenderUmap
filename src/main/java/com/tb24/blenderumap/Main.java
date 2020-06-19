/*
 * (C) 2020 amrsatrio. All rights reserved.
 */
package com.tb24.blenderumap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import kotlin.collections.MapsKt;
import kotlin.text.StringsKt;
import me.fungames.jfortniteparse.fileprovider.DefaultFileProvider;
import me.fungames.jfortniteparse.ue4.FGuid;
import me.fungames.jfortniteparse.ue4.assets.Package;
import me.fungames.jfortniteparse.ue4.assets.exports.UExport;
import me.fungames.jfortniteparse.ue4.assets.objects.FPackageIndex;
import me.fungames.jfortniteparse.ue4.assets.objects.FPropertyTag;
import me.fungames.jfortniteparse.ue4.assets.objects.FRotator;
import me.fungames.jfortniteparse.ue4.assets.objects.FSoftObjectPath;
import me.fungames.jfortniteparse.ue4.assets.objects.FStructFallback;
import me.fungames.jfortniteparse.ue4.assets.objects.FVector;
import me.fungames.jfortniteparse.ue4.assets.util.FName;
import me.fungames.jfortniteparse.ue4.assets.util.StructFallbackReflectionUtilKt;
import me.fungames.jfortniteparse.ue4.pak.GameFile;
import me.fungames.jfortniteparse.ue4.pak.PakFileReader;
import me.fungames.jfortniteparse.ue4.versions.GameKt;
import me.fungames.jfortniteparse.ue4.versions.Ue4Version;

import static com.tb24.blenderumap.JWPSerializer.GSON;

public class Main {
	private static final Logger LOGGER = LoggerFactory.getLogger("BlenderUmap");
	private static Config config;
	private static File jsonsFolder = new File("jsons");
	private static DefaultFileProvider provider;
	private static Map<GameFile, Package> loaded = new HashMap<>();
	private static Set<String> toExport = new HashSet<>();
	private static long start = System.currentTimeMillis();

	public static void main(String[] args) {
		try {
			File configFile = new File("config.json");

			if (!configFile.exists()) {
				LOGGER.error("config.json not found");
				return;
			}

			try (FileReader reader = new FileReader(configFile)) {
				config = GSON.fromJson(reader, Config.class);
			}

			File paksDir = new File(config.PaksDirectory);

			if (!paksDir.exists()) {
				throw new MainException("Directory " + paksDir.getAbsolutePath() + " not found.");
			}

			if (config.UEVersion == null) {
				throw new MainException("Please specify a valid UE version. Must be either of: " + Arrays.toString(Ue4Version.values()));
			}

			if (config.ExportPackage == null || config.ExportPackage.isEmpty()) {
				throw new MainException("Please specify ExportPackage.");
			}

			provider = new DefaultFileProvider(paksDir, config.UEVersion);
			Map<FGuid, byte[]> keysToSubmit = new HashMap<>();

			for (Config.EncryptionKey entry : config.EncryptionKeys) {
				if (isEmpty(entry.FileName)) {
					keysToSubmit.put(entry.Guid, entry.Key);
				} else {
					Optional<PakFileReader> foundGuid = provider.getUnloadedPaks().stream().filter(it -> it.getFileName().equals(entry.FileName)).findFirst();

					if (foundGuid.isPresent()) {
						keysToSubmit.put(foundGuid.get().getPakInfo().getEncryptionKeyGuid(), entry.Key);
					} else {
						LOGGER.warn("PAK file not found: " + entry.FileName);
					}
				}
			}

			provider.submitKeys(keysToSubmit);
			JsonArray components = exportAndProduceProcessed(config.ExportPackage);

			if (components == null) return;

			if (config.bRunUModel && !toExport.isEmpty()) {
				exportUmodel();
			}

			File file = new File("processed.json");
			LOGGER.info("Writing to " + file.getAbsolutePath());

			try (FileWriter writer = new FileWriter(file)) {
				GSON.toJson(components, writer);
			}

			LOGGER.info(String.format("All done in %,.1f sec. In the Python script, replace the line with data_dir with this line below:\n\ndata_dir = r\"%s\"", (System.currentTimeMillis() - start) / 1000.0F, new File("").getAbsolutePath()));
		} catch (Exception e) {
			if (e instanceof MainException) {
				LOGGER.info(e.getMessage());
			} else {
				LOGGER.error("Uncaught exception", e);
			}

			System.exit(1);
		}
	}

	private static JsonArray exportAndProduceProcessed(String s) {
		Package pkg = loadIfNot(s);

		if (pkg == null) {
			return null;
		} else if (!s.endsWith(".umap")) {
			LOGGER.info(s + " is not an .umap, won't try to export");
			return null;
		}

		JsonArray comps = new JsonArray();

		for (UExport export : pkg.getExports()) {
			String exportType = export.getExportType();

			if (exportType.equals("LODActor")) {
				continue;
			}

			FPackageIndex smc = getProp(export, "StaticMeshComponent", FPackageIndex.class);

			if (smc == null) {
				continue;
			}

			UExport refSMC = pkg.getExports().get(smc.getIndex() - 1);

			// identifiers
			JsonArray comp = new JsonArray();
			comps.add(comp);
			FGuid guid = getProp(export, "MyGuid", FGuid.class);
			comp.add(guid != null ? guidAsString(guid) : UUID.randomUUID().toString().replace("-", ""));
			comp.add(exportType);

			// region mesh
			String meshS = null;
			FPackageIndex mesh = getProp(refSMC, "StaticMesh", FPackageIndex.class);

			if (mesh == null || mesh.getIndex() == 0) { // read the actor class to find the mesh
				Package actorPkg = loadIfNot(export.getExport().getClassIndex().getOuterImportObject().getObjectName().getText());

				if (actorPkg != null) {
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
			// endregion

			JsonObject matsObj = new JsonObject();
			JsonArray textureDataArr = new JsonArray();
			List<Mat> materials = new ArrayList<>();

			if (mesh != null && mesh.getIndex() != 0) {
				toExport.add(meshS = mesh.getOuterImportObject().getObjectName().getText());

				if (config.bReadMaterials) {
					Package meshPkg = loadIfNot(meshS);

					if (meshPkg != null) {
						for (UExport meshExport : meshPkg.getExports()) {
							if (meshExport.getExportType().equals("StaticMesh")) {
								//ExportStaticMeshKt.export(StaticMeshesKt.convertMesh((UStaticMesh) meshExport)).writeToDir(new File("TestExportMesh/" + meshS.substring(1)).getParentFile());
								FStructFallback[] staticMaterials = getProp(meshExport, "StaticMaterials", FStructFallback[].class);

								if (staticMaterials != null) {
									for (FStructFallback staticMaterial : staticMaterials) {
										materials.add(new Mat(getProp(staticMaterial.getProperties(), "MaterialInterface", FPackageIndex.class)));
									}
								}

								break;
							}
						}
					}
				}
			}

			if (config.bReadMaterials) {
				FPackageIndex material = getProp(refSMC, "BaseMaterial", FPackageIndex.class);
				FPackageIndex[] overrideMaterials = getProp(export, "OverrideMaterials", FPackageIndex[].class);

				for (FPackageIndex textureData : getProps(export.getBaseObject().getProperties(), "TextureData", FPackageIndex.class)) {
					if (textureData != null && textureData.getIndex() != 0) {
						String textureDataPath = textureData.getOuterImportObject().getObjectName().getText();
						Package texDataPkg = loadIfNot(textureDataPath);

						if (texDataPkg != null) {
							BuildingTextureData td = StructFallbackReflectionUtilKt.mapToClass(texDataPkg.getExports().get(0).getBaseObject(), BuildingTextureData.class, null);
							JsonArray textures = new JsonArray();
							textures.add(td.Diffuse != null && td.Diffuse.getIndex() != 0 ? td.Diffuse.getOuterImportObject().getObjectName().getText() : null);
							textures.add(td.Normal != null && td.Normal.getIndex() != 0 ? td.Normal.getOuterImportObject().getObjectName().getText() : null);
							textures.add(td.Specular != null && td.Specular.getIndex() != 0 ? td.Specular.getOuterImportObject().getObjectName().getText() : null);
							textures.add(td.Emissive != null && td.Emissive.getIndex() != 0 ? td.Emissive.getOuterImportObject().getObjectName().getText() : null);
							textures.add(td.Mask != null && td.Mask.getIndex() != 0 ? td.Mask.getOuterImportObject().getObjectName().getText() : null);
							JsonArray entry = new JsonArray();
							entry.add(textureDataPath);
							entry.add(textures);
							textureDataArr.add(entry);

							if (td.OverrideMaterial != null && td.OverrideMaterial.getIndex() != 0) {
								material = td.OverrideMaterial;
							}
						}
					} else {
						textureDataArr.add((JsonElement) null);
					}
				}

				for (int i = 0; i < materials.size(); i++) {
					Mat mat = materials.get(i);

					if (material != null) {
						mat.name = overrideMaterials != null && i < overrideMaterials.length && overrideMaterials[i].getIndex() != 0 ? overrideMaterials[i] : material;
					}

					mat.populateTextures();
					mat.addToObj(matsObj);
				}
			}

			// region additional worlds
			JsonArray children = new JsonArray();
			FSoftObjectPath[] additionalWorlds = getProp(export, "AdditionalWorlds", FSoftObjectPath[].class);

			if (additionalWorlds != null) {
				for (FSoftObjectPath additionalWorld : additionalWorlds) {
					String text = additionalWorld.getAssetPathName().getText();
					children.add(exportAndProduceProcessed(StringsKt.substringBeforeLast(text, '.', text) + ".umap"));
				}
			}
			// endregion

			comp.add(mesh != null && mesh.getIndex() != 0 ? meshS : null);
			comp.add(matsObj);
			comp.add(textureDataArr);
			comp.add(vector(getProp(refSMC, "RelativeLocation", FVector.class)));
			comp.add(rotator(getProp(refSMC, "RelativeRotation", FRotator.class)));
			comp.add(vector(getProp(refSMC, "RelativeScale3D", FVector.class)));
			comp.add(children);
		}

		return comps;
	}

	private static Package loadIfNot(String pkg) {
		GameFile gameFile = provider.findGameFile(pkg);

		if (gameFile != null) {
			return loadIfNot(gameFile);
		} else {
			LOGGER.warn("Package " + pkg + " not found");
			return null;
		}
	}

	private static Package loadIfNot(GameFile pkg) {
		return MapsKt.getOrPut(loaded, pkg, () -> {
			LOGGER.info("Loading " + pkg);
			Package loadedPkg = provider.loadGameFile(pkg);

			if (loadedPkg != null && config.bDumpAssets) {
				File file = new File(jsonsFolder, pkg.getPathWithoutExtension() + ".json");
				LOGGER.info("Writing JSON to " + file.getAbsolutePath());
				file.getParentFile().mkdirs();

				try (FileWriter writer = new FileWriter(file)) {
					GSON.toJson(loadedPkg.getExports(), writer);
				} catch (IOException e) {
					LOGGER.error("Writing failed", e);
				}
			}

			return loadedPkg;
		});
	}

	private static void exportUmodel() throws InterruptedException, IOException {
		try (PrintWriter pw = new PrintWriter("umodel_cmd.txt")) {
			pw.println("-path=\"" + config.PaksDirectory + '\"');
			pw.println("-game=ue4." + GameKt.GAME_UE4_GET_MINOR(config.UEVersion.getGame()));

			if (config.EncryptionKeys.length > 0) {
				pw.println("-aes=0x" + ByteArrayUtils.encode(config.EncryptionKeys[0].Key));
			}

			pw.println("-out=\"" + new File("").getAbsolutePath() + '\"');

			if (!isEmpty(config.UModelAdditionalArgs)) {
				pw.println(config.UModelAdditionalArgs);
			}

			boolean bFirst = true;

			for (String export : toExport) {
				if (bFirst) {
					bFirst = false;
					pw.println("-export");
					pw.println(export);
				} else {
					pw.println("-pkg=" + export);
				}
			}
		}

		ProcessBuilder pb = new ProcessBuilder(Arrays.asList("umodel", "@umodel_cmd.txt"));
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		LOGGER.info("Starting UModel process");
		int exitCode = pb.start().waitFor();

		if (exitCode == 0) {
			toExport.clear();
		} else {
			LOGGER.warn("UModel returned exit code " + exitCode + ", some assets might weren't exported successfully");
		}
	}

	private static <T> T getProp(List<FPropertyTag> properties, String name, Class<T> clazz) {
		for (FPropertyTag prop : properties) {
			if (name.equals(prop.getName().getText())) {
				return (T) prop.getTagTypeValue(clazz, null);
			}
		}

		return null;
	}

	private static <T> T getProp(UExport export, String name, Class<T> clazz) {
		return getProp(export.getBaseObject().getProperties(), name, clazz);
	}

	public static <T> T[] getProps(List<FPropertyTag> properties, String name, Class<T> clazz) {
		List<FPropertyTag> collected = new ArrayList<>();
		int maxIndex = -1;

		for (FPropertyTag prop : properties) {
			if (prop.getName().getText().equals(name)) {
				collected.add(prop);
				maxIndex = Math.max(maxIndex, prop.getArrayIndex());
			}
		}

		T[] out = (T[]) Array.newInstance(clazz, maxIndex + 1);

		for (FPropertyTag prop : collected) {
			out[prop.getArrayIndex()] = (T) prop.getTagTypeValue(clazz, null);
		}

		return out;
	}

	private static String guidAsString(FGuid guid) {
		return String.format("%08x%08x%08x%08x", guid.getPart1(), guid.getPart2(), guid.getPart3(), guid.getPart4());
	}

	private static JsonArray vector(FVector vector) {
		if (vector == null) return null;
		JsonArray array = new JsonArray(3);
		array.add(vector.getX());
		array.add(vector.getY());
		array.add(vector.getZ());
		return array;
	}

	private static JsonArray rotator(FRotator rotator) {
		if (rotator == null) return null;
		JsonArray array = new JsonArray(3);
		array.add(rotator.getPitch());
		array.add(rotator.getYaw());
		array.add(rotator.getRoll());
		return array;
	}

	private static boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}

	private static class Mat {
		public FPackageIndex name;
		public Map<String, String> textureMap = new HashMap<>();

		public Mat(FPackageIndex name) {
			this.name = name;
		}

		public void populateTextures() {
			populateTextures(name);
		}

		public void populateTextures(FPackageIndex pkgIndex) {
			if (pkgIndex.getIndex() == 0) return;
			Package matPkg = loadIfNot(pkgIndex.getOuterImportObject().getObjectName().getText());
			if (matPkg == null) return;
			UExport matFirstExp = matPkg.getExports().get(0);
			FStructFallback[] textureParameterValues = getProp(matFirstExp, "TextureParameterValues", FStructFallback[].class);

			if (textureParameterValues != null) {
				for (FStructFallback textureParameterValue : textureParameterValues) {
					FName name = getProp(getProp(textureParameterValue.getProperties(), "ParameterInfo", FStructFallback.class).getProperties(), "Name", FName.class);

					if (name != null) {
						FPackageIndex parameterValue = getProp(textureParameterValue.getProperties(), "ParameterValue", FPackageIndex.class);

						if (parameterValue != null && parameterValue.getIndex() != 0 && !textureMap.containsKey(name.getText())) {
							textureMap.put(name.getText(), parameterValue.getOuterImportObject().getObjectName().getText());
						}
					}
				}
			}

			FPackageIndex parent = getProp(matFirstExp, "Parent", FPackageIndex.class);

			if (parent != null && parent.getIndex() != 0) {
				populateTextures(parent);
			}
		}

		public void addToObj(JsonObject obj) {
			String[][] textures = { // d n s e a
					{
							textureMap.getOrDefault("Trunk_BaseColor", textureMap.get("Diffuse")),
							textureMap.getOrDefault("Trunk_Normal", textureMap.get("Normals")),
							textureMap.getOrDefault("Trunk_Specular", textureMap.get("SpecularMasks")),
							textureMap.get("EmissiveTexture"),
							textureMap.get("MaskTexture")
					},
					{
							textureMap.get("Diffuse_Texture_3"),
							textureMap.get("Normals_Texture_3"),
							textureMap.get("SpecularMasks_3"),
							textureMap.get("EmissiveTexture_3"),
							textureMap.get("MaskTexture_3")
					},
					{
							textureMap.get("Diffuse_Texture_4"),
							textureMap.get("Normals_Texture_4"),
							textureMap.get("SpecularMasks_4"),
							textureMap.get("EmissiveTexture_4"),
							textureMap.get("MaskTexture_4")
					},
					{
							textureMap.get("Diffuse_Texture_2"),
							textureMap.get("Normals_Texture_2"),
							textureMap.get("SpecularMasks_2"),
							textureMap.get("EmissiveTexture_2"),
							textureMap.get("MaskTexture_2")
					}
			};

			for (int i = 0; i < textures.length; i++) {
				boolean empty = true;

				for (int j = 0; j < textures[i].length; j++) {
					empty &= textures[i][j] == null;

					if (textures[i][j] != null) {
						toExport.add(textures[i][j]);
					}
				}

				if (empty) {
					textures[i] = new String[0];
				}
			}

			obj.add(name.getIndex() != 0 ? name.getOuterImportObject().getObjectName().getText() : Integer.toHexString(hashCode()), GSON.toJsonTree(textures));
		}
	}

	private static class BuildingTextureData {
		public FPackageIndex Diffuse;
		public FPackageIndex Normal;
		public FPackageIndex Specular;
		public FPackageIndex Emissive;
		public FPackageIndex Mask;
		public FPackageIndex OverrideMaterial;
		// public EFortResourceType ResourceType;
		// public Float ResourceCost;
	}

	private static class Config {
		public String PaksDirectory = "C:\\Program Files\\Epic Games\\Fortnite\\FortniteGame\\Content\\Paks";
		public Ue4Version UEVersion = Ue4Version.GAME_UE4_LATEST;
		public EncryptionKey[] EncryptionKeys = {};
		public boolean bReadMaterials = false;
		public boolean bRunUModel = true;
		public String UModelAdditionalArgs = "";
		public boolean bDumpAssets = false;
		public String ExportPackage;

		private static class EncryptionKey {
			public FGuid Guid = FGuid.Companion.getMainGuid();
			public String FileName;
			public byte[] Key = {};
		}
	}

	private static class MainException extends Exception {
		public MainException(String message) {
			super(message);
		}
	}
}
