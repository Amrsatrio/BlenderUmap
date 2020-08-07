/*
 * (C) amrsatrio. All rights reserved.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import kotlin.text.StringsKt;
import me.fungames.jfortniteparse.ue4.assets.Package;
import me.fungames.jfortniteparse.ue4.assets.exports.UExport;
import me.fungames.jfortniteparse.ue4.assets.exports.UObject;
import me.fungames.jfortniteparse.ue4.assets.exports.UStaticMesh;
import me.fungames.jfortniteparse.ue4.assets.exports.tex.UTexture2D;
import me.fungames.jfortniteparse.ue4.assets.objects.FStructFallback;
import me.fungames.jfortniteparse.ue4.assets.util.StructFallbackReflectionUtilKt;
import me.fungames.jfortniteparse.ue4.converters.meshes.StaticMeshesKt;
import me.fungames.jfortniteparse.ue4.converters.meshes.psk.ExportStaticMeshKt;
import me.fungames.jfortniteparse.ue4.converters.textures.TexturesKt;
import me.fungames.jfortniteparse.ue4.objects.core.math.FRotator;
import me.fungames.jfortniteparse.ue4.objects.core.math.FVector;
import me.fungames.jfortniteparse.ue4.objects.core.misc.FGuid;
import me.fungames.jfortniteparse.ue4.objects.uobject.FName;
import me.fungames.jfortniteparse.ue4.objects.uobject.FObjectExport;
import me.fungames.jfortniteparse.ue4.objects.uobject.FPackageIndex;
import me.fungames.jfortniteparse.ue4.objects.uobject.FSoftObjectPath;
import me.fungames.jfortniteparse.ue4.versions.GameKt;
import me.fungames.jfortniteparse.ue4.versions.Ue4Version;

import static com.tb24.blenderumap.AssetUtilsKt.asString;
import static com.tb24.blenderumap.AssetUtilsKt.getProp;
import static com.tb24.blenderumap.AssetUtilsKt.getProps;
import static com.tb24.blenderumap.JWPSerializer.GSON;

public class Main {
	private static final Logger LOGGER = LoggerFactory.getLogger("BlenderUmap");
	private static Config config;
	private static MyFileProvider provider;
	private static Set<FPackageIndex> exportQueue = new HashSet<>();
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

			provider = new MyFileProvider(paksDir, config.UEVersion, config.EncryptionKeys, config.bDumpAssets);

			Package pkg = exportAndProduceProcessed(config.ExportPackage);
			if (pkg == null) return;

			if (!exportQueue.isEmpty()) {
				exportUmodel();
			}

			File file = new File("processed.json");
			LOGGER.info("Writing to " + file.getAbsolutePath());

			try (FileWriter writer = new FileWriter(file)) {
//				GSON.toJson(components, writer);
				GSON.toJson(MyFileProvider.compactFilePath(pkg.getName()), writer);
			}

			LOGGER.info(String.format("All done in %,.1f sec. In the Python script, replace the line with data_dir with this line below:\n\ndata_dir = r\"%s\"", (System.currentTimeMillis() - start) / 1000.0F, new File("").getAbsolutePath()));
		} catch (Exception e) {
			if (e instanceof MainException) {
				LOGGER.info(e.getMessage());
			} else {
				LOGGER.error("An unexpected error has occurred, please report", e);
			}

			System.exit(1);
		}
	}

	private static Package exportAndProduceProcessed(String s) throws Exception {
		Package pkg = provider.loadGameFile(s);

		if (pkg == null) {
			return null;
		} else if (!s.endsWith(".umap")) {
			LOGGER.info(s + " is not an .umap, won't try to export");
			return null;
		}

		JsonArray comps = new JsonArray();

		for (FObjectExport objectExport : pkg.getExportMap()) {
			UExport export = objectExport.exportObject.getValue();
			String exportType = export.getExportType();
			if (exportType.equals("LODActor")) continue;

			UExport staticMeshExp = provider.loadObject(getProp(export, "StaticMeshComponent", FPackageIndex.class));
			if (staticMeshExp == null) continue;

			// identifiers
			JsonArray comp = new JsonArray();
			comps.add(comp);
			FGuid guid = getProp(export, "MyGuid", FGuid.class);
			comp.add(guid != null ? asString(guid) : UUID.randomUUID().toString().replace("-", ""));
			comp.add(objectExport.getObjectName().getText());

			// region mesh
			FPackageIndex mesh = getProp(staticMeshExp, "StaticMesh", FPackageIndex.class);

			if (mesh == null || mesh.getIndex() == 0) { // read the actor class to find the mesh
				UExport actorBlueprint = provider.loadObject(objectExport.getClassIndex());

				if (actorBlueprint != null) {
					for (UExport actorExp : actorBlueprint.getOwner().getExports()) {
						if (actorExp.getExportType().endsWith("StaticMeshComponent") && (mesh = getProp(actorExp, "StaticMesh", FPackageIndex.class)) != null && mesh.getIndex() != 0) {
							break;
						}
					}
				}
			}
			// endregion

			JsonObject matsObj = new JsonObject();
			JsonArray textureDataArr = new JsonArray();
			List<Mat> materials = new ArrayList<>();

			if (mesh != null && mesh.getIndex() != 0) {
				UExport meshExport = provider.loadObject(mesh);

				if (meshExport != null) {
					if (config.bUseUModel) {
						exportQueue.add(mesh);
					} else {
						ExportStaticMeshKt.export(StaticMeshesKt.convertMesh((UStaticMesh) meshExport), false, false).writeToDir(getExportDir(meshExport));
					}

					if (config.bReadMaterials) {
						FStructFallback[] staticMaterials = getProp(meshExport, "StaticMaterials", FStructFallback[].class);

						if (staticMaterials != null) {
							for (FStructFallback staticMaterial : staticMaterials) {
								materials.add(new Mat(getProp(staticMaterial.getProperties(), "MaterialInterface", FPackageIndex.class)));
							}
						}
					}
				}
			}

			if (config.bReadMaterials) {
				FPackageIndex material = getProp(staticMeshExp, "BaseMaterial", FPackageIndex.class);
				FPackageIndex[] overrideMaterials = getProp(export, "OverrideMaterials", FPackageIndex[].class);

				for (FPackageIndex textureDataIdx : getProps(((UObject) export).getProperties(), "TextureData", FPackageIndex.class)) {
					UObject texDataExp = (UObject) provider.loadObject(textureDataIdx);

					if (texDataExp != null) {
						BuildingTextureData td = StructFallbackReflectionUtilKt.mapToClass(texDataExp, BuildingTextureData.class);
						JsonArray textures = new JsonArray();
						addToArray(textures, td.Diffuse);
						addToArray(textures, td.Normal);
						addToArray(textures, td.Specular);
						addToArray(textures, td.Emissive);
						addToArray(textures, td.Mask);
						JsonArray entry = new JsonArray();
						entry.add(pkgIndexToDirPath(textureDataIdx));
						entry.add(textures);
						textureDataArr.add(entry);

						if (td.OverrideMaterial != null && td.OverrideMaterial.getIndex() != 0) {
							material = td.OverrideMaterial;
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
					Package cpkg = exportAndProduceProcessed(StringsKt.substringBeforeLast(text, '.', text) + ".umap");
					children.add(cpkg != null ? MyFileProvider.compactFilePath(cpkg.getName()) : null);
				}
			}
			// endregion

			comp.add(pkgIndexToDirPath(mesh));
			comp.add(matsObj);
			comp.add(textureDataArr);
			comp.add(vector(getProp(staticMeshExp, "RelativeLocation", FVector.class)));
			comp.add(rotator(getProp(staticMeshExp, "RelativeRotation", FRotator.class)));
			comp.add(vector(getProp(staticMeshExp, "RelativeScale3D", FVector.class)));
			comp.add(children);
		}

		File file = new File(MyFileProvider.JSONS_FOLDER, MyFileProvider.compactFilePath(pkg.getName()) + ".processed.json");
		file.getParentFile().mkdirs();
		LOGGER.info("Writing to " + file.getAbsolutePath());

		try (FileWriter writer = new FileWriter(file)) {
			GSON.toJson(comps, writer);
		} catch (IOException e) {
			LOGGER.error("Writing failed", e);
		}

		return pkg;
	}

	private static void addToArray(JsonArray array, FPackageIndex index) {
		if (index != null && index.getIndex() != 0) {
			exportTexture(index);
			array.add(pkgIndexToDirPath(index));
		} else {
			array.add((JsonElement) null);
		}
	}

	private static void exportTexture(FPackageIndex index) {
		if (config.bUseUModel) {
			exportQueue.add(index);
			return;
		}

		try {
			UTexture2D texExport = (UTexture2D) provider.loadObject(index);
			File output = new File(getExportDir(texExport), texExport.getName() + ".png");

			if (output.exists()) {
				LOGGER.debug("Texture already exists, skipping: " + output.getAbsolutePath());
			} else {
				LOGGER.info("Saving texture to " + output.getAbsolutePath());
				ImageIO.write(TexturesKt.toBufferedImage(texExport), "png", output);
			}
		} catch (IOException e) {
			LOGGER.warn("Failed to save texture", e);
		}
	}

	public static File getExportDir(UExport exportObj) {
		String pkgPath = MyFileProvider.compactFilePath(exportObj.getOwner().getName());
		pkgPath = StringsKt.substringBeforeLast(pkgPath, '.', pkgPath);

		if (pkgPath.startsWith("/")) {
			pkgPath = pkgPath.substring(1);
		}

		File outputDir = new File(pkgPath).getParentFile();
		String pkgName = StringsKt.substringAfterLast(pkgPath, '/', pkgPath);

		if (!exportObj.getName().equals(pkgName)) {
			outputDir = new File(outputDir, pkgName);
		}

		outputDir.mkdirs();
		return outputDir;
	}

	public static String pkgIndexToDirPath(FPackageIndex index) {
		if (index == null) return null;

		int i = index.getIndex();
		if (i == 0) return null;

		String pkgPath = MyFileProvider.compactFilePath(index.getOwner().getName());
		pkgPath = StringsKt.substringBeforeLast(pkgPath, '.', pkgPath);
		pkgPath = i > 0 ? pkgPath : index.getOuterImportObject().getObjectName().getText();
		String objectName = index.getResource().getObjectName().getText();
		return StringsKt.substringAfterLast(pkgPath, '/', pkgPath).equals(objectName) ? pkgPath : pkgPath + '/' + objectName;
	}

	private static void exportUmodel() throws InterruptedException, IOException {
		try (PrintWriter pw = new PrintWriter("umodel_cmd.txt")) {
			pw.println("-path=\"" + config.PaksDirectory + '\"');
			pw.println("-game=ue4." + GameKt.GAME_UE4_GET_MINOR(config.UEVersion.getGame()));

			if (config.EncryptionKeys.size() > 0) { // TODO run umodel multiple times if there's more than one encryption key
				pw.println("-aes=0x" + ByteArrayUtils.encode(config.EncryptionKeys.get(0).Key));
			}

			pw.println("-out=\"" + new File("").getAbsolutePath() + '\"');

			if (!isEmpty(config.UModelAdditionalArgs)) {
				pw.println(config.UModelAdditionalArgs);
			}

			boolean bFirst = true;

			for (FPackageIndex export : exportQueue) {
				int i = export.getIndex();
				if (i == 0) continue;

				String packagePath = i > 0 ? MyFileProvider.compactFilePath(export.getOwner().getName()) : export.getOuterImportObject().getObjectName().getText();
				String objectName = (i > 0 ? export.getExportObject().getObjectName() : export.getImportObject().getObjectName()).getText();

				if (bFirst) {
					bFirst = false;
					pw.println("-export");
					pw.println(packagePath);
					pw.println(objectName);
				} else {
					pw.println("-pkg=" + packagePath);
					pw.println("-obj=" + objectName);
				}
			}
		}

		ProcessBuilder pb = new ProcessBuilder(Arrays.asList("umodel", "@umodel_cmd.txt"));
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		LOGGER.info("Starting UModel process");
		int exitCode = pb.start().waitFor();

		if (exitCode == 0) {
			exportQueue.clear();
		} else {
			LOGGER.warn("UModel returned exit code " + exitCode + ", some assets might weren't exported successfully");
		}
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
		public Map<String, FPackageIndex> textureMap = new HashMap<>();

		public Mat(FPackageIndex name) {
			this.name = name;
		}

		public void populateTextures() {
			populateTextures(name);
		}

		public void populateTextures(FPackageIndex pkgIndex) {
			if (pkgIndex.getIndex() == 0) return;

			UExport material = provider.loadObject(pkgIndex);
			if (material == null) return;

			FStructFallback[] textureParameterValues = getProp(material, "TextureParameterValues", FStructFallback[].class);

			if (textureParameterValues != null) {
				for (FStructFallback textureParameterValue : textureParameterValues) {
					FName name = getProp(getProp(textureParameterValue.getProperties(), "ParameterInfo", FStructFallback.class).getProperties(), "Name", FName.class);

					if (name != null) {
						FPackageIndex parameterValue = getProp(textureParameterValue.getProperties(), "ParameterValue", FPackageIndex.class);

						if (parameterValue != null && parameterValue.getIndex() != 0 && !textureMap.containsKey(name.getText())) {
							textureMap.put(name.getText(), parameterValue);
						}
					}
				}
			}

			FPackageIndex parent = getProp(material, "Parent", FPackageIndex.class);

			if (parent != null && parent.getIndex() != 0) {
				populateTextures(parent);
			}
		}

		public void addToObj(JsonObject obj) {
			if (name.getIndex() == 0) {
				obj.add(Integer.toHexString(hashCode()), null);
				return;
			}

			FPackageIndex[][] textures = { // d n s e a
					{
							textureMap.getOrDefault("Trunk_BaseColor", textureMap.getOrDefault("Diffuse", textureMap.get("DiffuseTexture"))),
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

			JsonArray array = new JsonArray(textures.length);

			for (FPackageIndex[] texture : textures) {
				boolean empty = true;

				for (FPackageIndex index : texture) {
					empty &= index == null || index.getIndex() == 0;

					if (index != null && index.getIndex() != 0) {
						exportTexture(index);
					}
				}

				JsonArray subArray = new JsonArray(texture.length);

				if (!empty) {
					for (FPackageIndex index : texture) {
						subArray.add(pkgIndexToDirPath(index));
					}
				}

				array.add(subArray);
			}

			obj.add(pkgIndexToDirPath(name), array);
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

	public static class Config {
		public String PaksDirectory = "C:\\Program Files\\Epic Games\\Fortnite\\FortniteGame\\Content\\Paks";
		public Ue4Version UEVersion = Ue4Version.GAME_UE4_LATEST;
		public List<MyFileProvider.EncryptionKey> EncryptionKeys = Collections.emptyList();
		public boolean bReadMaterials = false;
		public boolean bUseUModel = true;
		public String UModelAdditionalArgs = "";
		public boolean bDumpAssets = false;
		public String ExportPackage;
	}

	private static class MainException extends Exception {
		public MainException(String message) {
			super(message);
		}
	}
}
