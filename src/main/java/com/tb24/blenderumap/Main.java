/*
 * (C) amrsatrio. All rights reserved.
 */
package com.tb24.blenderumap;

import com.google.gson.FieldNamingPolicy;
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

import kotlin.Lazy;
import kotlin.text.StringsKt;
import me.fungames.jfortniteparse.fort.exports.BuildingTextureData;
import me.fungames.jfortniteparse.fort.exports.actors.BuildingSMActor;
import me.fungames.jfortniteparse.ue4.assets.Package;
import me.fungames.jfortniteparse.ue4.assets.exports.UObject;
import me.fungames.jfortniteparse.ue4.assets.exports.UStaticMesh;
import me.fungames.jfortniteparse.ue4.assets.exports.UStruct;
import me.fungames.jfortniteparse.ue4.assets.exports.components.UStaticMeshComponent;
import me.fungames.jfortniteparse.ue4.assets.exports.mats.UMaterialInstance;
import me.fungames.jfortniteparse.ue4.assets.exports.mats.UMaterialInstance.FTextureParameterValue;
import me.fungames.jfortniteparse.ue4.assets.exports.mats.UMaterialInterface;
import me.fungames.jfortniteparse.ue4.assets.exports.tex.UTexture;
import me.fungames.jfortniteparse.ue4.assets.exports.tex.UTexture2D;
import me.fungames.jfortniteparse.ue4.assets.objects.meshes.FStaticMaterial;
import me.fungames.jfortniteparse.ue4.assets.util.StructFallbackReflectionUtilKt;
import me.fungames.jfortniteparse.ue4.converters.meshes.StaticMeshesKt;
import me.fungames.jfortniteparse.ue4.converters.meshes.psk.ExportStaticMeshKt;
import me.fungames.jfortniteparse.ue4.converters.textures.TexturesKt;
import me.fungames.jfortniteparse.ue4.objects.core.math.FRotator;
import me.fungames.jfortniteparse.ue4.objects.core.math.FVector;
import me.fungames.jfortniteparse.ue4.objects.core.misc.FGuid;
import me.fungames.jfortniteparse.ue4.objects.uobject.FName;
import me.fungames.jfortniteparse.ue4.objects.uobject.FSoftObjectPath;
import me.fungames.jfortniteparse.ue4.versions.GameKt;
import me.fungames.jfortniteparse.ue4.versions.Ue4Version;

import static com.tb24.blenderumap.AssetUtilsKt.getProp;
import static com.tb24.blenderumap.JWPSerializer.GSON;

public class Main {
	private static final Logger LOGGER = LoggerFactory.getLogger("BlenderUmap");
	private static Config config;
	private static MyFileProvider provider;
	private static final Set<Lazy<? extends UObject>> exportQueue = new HashSet<>();
	private static final long start = System.currentTimeMillis();

	public static void main(String[] args) {
		try {
			File configFile = new File("config.json");

			if (!configFile.exists()) {
				LOGGER.error("config.json not found");
				return;
			}

			LOGGER.info("Reading config file " + configFile.getAbsolutePath());

			try (FileReader reader = new FileReader(configFile)) {
				config = GSON.newBuilder().setFieldNamingStrategy(FieldNamingPolicy.IDENTITY).create().fromJson(reader, Config.class);
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
				String pkgName = provider.compactFilePath(pkg.getName());
				if (!pkgName.endsWith(".umap")) {
					pkgName += ".umap";
				}
				GSON.toJson(pkgName, writer);
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

	private static Package exportAndProduceProcessed(String s) {
		Package pkg = provider.loadGameFile(s);

		/*if (!s.endsWith(".umap")) {
			LOGGER.info("{} is not an .umap, won't try to export", s);
			return null;
		}*/

		JsonArray comps = new JsonArray();

		for (UObject object : pkg.getExports()) {
			if (!(object instanceof BuildingSMActor)) {
				continue;
			}
			BuildingSMActor actor = (BuildingSMActor) object;

			UStaticMeshComponent staticMeshExp = actor.StaticMeshComponent.getValue();
			if (staticMeshExp == null) continue;

			// identifiers
			JsonArray comp = new JsonArray();
			comps.add(comp);
			FGuid guid = actor.MyGuid;
			comp.add(guid != null ? guid.toString() : UUID.randomUUID().toString().replace("-", ""));
			comp.add(actor.getName());

			// region mesh
			Lazy<UStaticMesh> mesh = staticMeshExp.StaticMesh;

			if (mesh == null) { // read the actor class to find the mesh
				UStruct actorBlueprint = actor.getClazz();

				if (actorBlueprint != null) {
					for (UObject actorExp : actorBlueprint.getOwner().getExports()) {
						if (actorExp instanceof UStaticMeshComponent && (mesh = ((UStaticMeshComponent) actorExp).StaticMesh) != null) {
							break;
						}
					}
				}
			}
			// endregion

			JsonObject matsObj = new JsonObject();
			JsonArray textureDataArr = new JsonArray();
			List<Mat> materials = new ArrayList<>();

			if (mesh != null) {
				UStaticMesh meshExport = mesh.getValue();

				if (meshExport != null) {
					if (config.bUseUModel) {
						exportQueue.add(mesh);
					} else {
						ExportStaticMeshKt.export(StaticMeshesKt.convertMesh(meshExport), false, false).writeToDir(getExportDir(meshExport));
					}

					if (config.bReadMaterials) {
						List<FStaticMaterial> staticMaterials = meshExport.StaticMaterials;

						if (staticMaterials != null) {
							for (FStaticMaterial staticMaterial : staticMaterials) {
								materials.add(new Mat(staticMaterial.materialInterface));
							}
						}
					}
				}
			}

			if (config.bReadMaterials) {
				Lazy<UMaterialInterface> material = actor.BaseMaterial;
				List<Lazy<UMaterialInterface>> overrideMaterials = staticMeshExp.OverrideMaterials;
				Lazy<BuildingTextureData>[] textureDataArr_ = actor.TextureData != null ? actor.TextureData : new Lazy[0];

				for (Lazy<BuildingTextureData> textureDataIdx : textureDataArr_) {
					BuildingTextureData texDataExp = textureDataIdx != null ? textureDataIdx.getValue() : null;

					if (texDataExp != null) {
						BuildingTextureData td = StructFallbackReflectionUtilKt.mapToClass(texDataExp, BuildingTextureData.class);
						JsonArray textures = new JsonArray();
						addToArray(textures, td.Diffuse);
						addToArray(textures, td.Normal);
						addToArray(textures, td.Specular);
						JsonArray entry = new JsonArray();
						entry.add(pkgIndexToDirPath(textureDataIdx));
						entry.add(textures);
						textureDataArr.add(entry);

						if (td.OverrideMaterial != null) {
							material = td.OverrideMaterial;
						}
					} else {
						textureDataArr.add((JsonElement) null);
					}
				}

				for (int i = 0; i < materials.size(); i++) {
					Mat mat = materials.get(i);

					if (material != null) {
						mat.name = overrideMaterials != null && i < overrideMaterials.size() && overrideMaterials.get(i) != null ? overrideMaterials.get(i) : material;
					}

					mat.populateTextures();
					mat.addToObj(matsObj);
				}
			}

			// region additional worlds
			JsonArray children = new JsonArray();
			FSoftObjectPath[] additionalWorlds = getProp(actor, "AdditionalWorlds", FSoftObjectPath[].class);

			if (additionalWorlds != null) {
				for (FSoftObjectPath additionalWorld : additionalWorlds) {
					String text = additionalWorld.getAssetPathName().getText();
					Package cpkg = exportAndProduceProcessed(StringsKt.substringBeforeLast(text, '.', text) + ".umap");
					children.add(cpkg != null ? provider.compactFilePath(cpkg.getName()) : null);
				}
			}
			// endregion

			comp.add(pkgIndexToDirPath(mesh));
			comp.add(matsObj);
			comp.add(textureDataArr);
			comp.add(vector(staticMeshExp.RelativeLocation));
			comp.add(rotator(staticMeshExp.RelativeRotation));
			comp.add(vector(staticMeshExp.RelativeScale3D));
			comp.add(children);
		}

		String pkgName = provider.compactFilePath(pkg.getName());
		if (!pkgName.endsWith(".umap")) {
			pkgName += ".umap";
		}
		File file = new File(MyFileProvider.JSONS_FOLDER, pkgName + ".processed.json");
		file.getParentFile().mkdirs();
		LOGGER.info("Writing to {}", file.getAbsolutePath());

		try (FileWriter writer = new FileWriter(file)) {
			GSON.toJson(comps, writer);
		} catch (IOException e) {
			LOGGER.error("Writing failed", e);
		}

		return pkg;
	}

	private static void addToArray(JsonArray array, Lazy<? extends UTexture> index) {
		if (index != null) {
			exportTexture(index);
			array.add(pkgIndexToDirPath(index));
		} else {
			array.add((JsonElement) null);
		}
	}

	private static void exportTexture(Lazy<? extends UTexture> index) {
		if (config.bUseUModel) {
			exportQueue.add(index);
			return;
		}

		try {
			UTexture texExport = index.getValue();
			File output = new File(getExportDir(texExport), texExport.getName() + ".png");

			if (output.exists()) {
				LOGGER.debug("Texture already exists, skipping: {}", output.getAbsolutePath());
			} else {
				LOGGER.info("Saving texture to {}", output.getAbsolutePath());
				ImageIO.write(TexturesKt.toBufferedImage((UTexture2D) texExport), "png", output);
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to save texture", e);
		}
	}

	public static File getExportDir(UObject exportObj) {
		String pkgPath = provider.compactFilePath(exportObj.getOwner().getName());
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

	public static String pkgIndexToDirPath(Lazy<? extends UObject> index) {
		if (index == null) return null;

		UObject object;
		try {
			object = index.getValue();
		} catch (Exception e) {
			LOGGER.warn("Failed to load object", e);
			return null;
		}
		String pkgPath = provider.compactFilePath(object.getOwner().getName());
		pkgPath = StringsKt.substringBeforeLast(pkgPath, '.', pkgPath);
		String objectName = object.getName();
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

			for (Lazy<? extends UObject> export : exportQueue) {
				if (export == null) continue;

				UObject object = export.getValue();
				String packagePath = provider.compactFilePath(object.getOwner().getName());
				String objectName = object.getName();

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
		public Lazy<? extends UMaterialInterface> name;
		public Map<String, Lazy<UTexture>> textureMap = new HashMap<>();

		public Mat(Lazy<? extends UMaterialInterface> name) {
			this.name = name;
		}

		public void populateTextures() {
			populateTextures(name);
		}

		public void populateTextures(Lazy<? extends UMaterialInterface> pkgIndex) {
			if (pkgIndex == null) return;

			UObject object = pkgIndex.getValue();
			if (!(object instanceof UMaterialInstance)) return;
			UMaterialInstance material = (UMaterialInstance) object;

			List<FTextureParameterValue> textureParameterValues = material.TextureParameterValues;

			if (textureParameterValues != null) {
				for (FTextureParameterValue textureParameterValue : textureParameterValues) {
					FName name = textureParameterValue.ParameterInfo.Name;

					if (name != null) {
						Lazy<UTexture> parameterValue = textureParameterValue.ParameterValue;

						if (parameterValue != null && !textureMap.containsKey(name.getText())) {
							textureMap.put(name.getText(), parameterValue);
						}
					}
				}
			}

			if (material.Parent != null) {
				populateTextures(material.Parent);
			}
		}

		public void addToObj(JsonObject obj) {
			if (name == null) {
				obj.add(Integer.toHexString(hashCode()), null);
				return;
			}

			Lazy[][] textures = { // d n s e a
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

			for (Lazy[] texture : textures) {
				boolean empty = true;

				for (Lazy<UTexture> index : texture) {
					empty &= index == null;

					if (index != null) {
						exportTexture(index);
					}
				}

				JsonArray subArray = new JsonArray(texture.length);

				if (!empty) {
					for (Lazy<UTexture> index : texture) {
						subArray.add(pkgIndexToDirPath(index));
					}
				}

				array.add(subArray);
			}

			obj.add(pkgIndexToDirPath(name), array);
		}
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
