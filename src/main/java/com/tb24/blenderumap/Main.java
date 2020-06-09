package com.tb24.blenderumap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import kotlin.collections.CollectionsKt;
import kotlin.collections.MapsKt;
import me.fungames.jfortniteparse.fileprovider.DefaultFileProvider;
import me.fungames.jfortniteparse.ue4.FGuid;
import me.fungames.jfortniteparse.ue4.assets.Package;
import me.fungames.jfortniteparse.ue4.assets.exports.UExport;
import me.fungames.jfortniteparse.ue4.assets.exports.UObject;
import me.fungames.jfortniteparse.ue4.assets.objects.FPackageIndex;
import me.fungames.jfortniteparse.ue4.assets.objects.FPropertyTag;
import me.fungames.jfortniteparse.ue4.assets.objects.FPropertyTagType;
import me.fungames.jfortniteparse.ue4.assets.objects.FRotator;
import me.fungames.jfortniteparse.ue4.assets.objects.FStructFallback;
import me.fungames.jfortniteparse.ue4.assets.objects.FVector;
import me.fungames.jfortniteparse.ue4.assets.objects.UScriptArray;
import me.fungames.jfortniteparse.ue4.pak.GameFile;
import me.fungames.jfortniteparse.ue4.versions.Ue4Version;

/**
 * @author amrsatrio
 */
public class Main {
	public static final String GAME_PATH = "C:\\Program Files\\Epic Games\\Fortnite\\FortniteGame\\Content\\Paks";
	public static final String AES = "0x3f3717f4f206ff21bda8d3bf62b323556d1d2e7d9b0f7abd572d3cfe5b569fac";
	private static final boolean READ_MATERIALS = false;
	private static Map<GameFile, Package> loaded = new HashMap<>();
	private static Set<String> toExport = new HashSet<>();

	public static void main(String[] args) throws Exception {
		try {
			DefaultFileProvider provider = new DefaultFileProvider(new File(GAME_PATH), Ue4Version.GAME_UE4_24);
			provider.submitKey(FGuid.Companion.getMainGuid(), AES);
			String s = "/Game/Athena/Apollo/Maps/Buildings/3x3/Apollo_3x3_BoatRental.umap";
//			s = "FortniteGame/Content/Athena/Apollo/Maps/Buildings/1x1/Apollo_1x1_BoatHouse_b.umap";
//			s = "FortniteGame/Content/Environments/Apollo/Sets/Rural/Materials/MI_Apollo_Rural_House.uasset";
//			s = "FortniteGame/Content/Maps/UI/BP12/Frontend_BP12_Room_Midas_Art.umap";
//			s = "FortniteGame/Content/Athena/Apollo/Maps/POI/Apollo_POI_Agency.umap";
//			s = "FortniteGame/Content/Athena/Apollo/Maps/POI/Apollo_POI_Agency_FT_b.umap";
//			s = "FortniteGame/Content/Athena/Apollo/Maps/POI/Apollo_POI_PleasantPark_001.umap";
//			s = "FortniteGame/Content/Athena/Apollo/Maps/POI/Apollo_POI_RiskyReels_001.umap";
//			s = "FortniteGame/Content/Athena/Apollo/Maps/POI/Apollo_POI_Yacht_001.umap";
//			s = "FortniteGame/Content/Athena/Apollo/Maps/POI/Apollo_POI_Yacht_002.umap";
//			s = "FortniteGame/Content/Athena/Apollo/Maps/Buildings/3x3/Apollo_3x3_FoodTruck_IceCream_a.umap";
//			s = "FortniteGame/Content/Athena/Apollo/Maps/Buildings/3x3/Apollo_3x3_Dam_PowerBox.umap";
//			s = "FortniteGame/Content/Athena/Apollo/Environments/BuildingActors/Wood/Boardwalk/Archways/Apollo_Boardwalk_ArchwayLong.uasset";
//			s = findBuildingActor(provider, "Apollo_Boardwalk_ArchwayLong_C");
//			s = findBuildingActor(provider, "Prop_WildWest_SimpleChair_02_C").getPath();
			Package pkg = provider.loadGameFile(s);
			List<UExport> exports = pkg.getExports();
			File file = new File(s.substring(s.lastIndexOf('/') + 1, s.lastIndexOf('.')) + ".json");
			System.out.println("Writing to " + file.getAbsolutePath());

			try (FileWriter writer = new FileWriter(file)) {
				JWPSerializer.GSON.toJson(exports, writer);
			}

			if (!s.endsWith(".umap")) return;

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

				FVector loc = getProp(refSMC, "RelativeLocation", FVector.class);
				FRotator rot = getProp(refSMC, "RelativeRotation", FRotator.class);
				FVector sc = getProp(refSMC, "RelativeScale3D", FVector.class);

				obj.add(mesh != null ? meshS : null);
				obj.add(matToUse);
				obj.add(JWPSerializer.GSON.toJsonTree(new String[4]));
				obj.add(loc != null ? vector(loc) : null);
				obj.add(rot != null ? rotator(rot) : null);
				obj.add(sc != null ? vector(sc) : null);
				// TODO parent attach
			}

			if (!toExport.isEmpty()) {
				exportUmodel();
			}

			if (READ_MATERIALS) // THESE MATERIALS SHIT TAKES FOREVER HELP ME FIX THX
				for (JsonElement entry : bruh) {
					JsonArray entry1 = (JsonArray) entry;
					JsonElement mat = entry1.get(3);

					if (mat.isJsonPrimitive()) {
						String[] matTex = new String[4];

						for (String s1 : Files.readAllLines(new File(fix(null, mat.getAsString().substring(1)) + ".mat").toPath())) {
							String[] split = s1.split("=");

							if (split.length > 1) {
								String assign = split[1].toLowerCase() + ".ubulk";
								Collection<GameFile> filtered = MapsKt.filter(provider.getFiles(), entry2 -> entry2.getKey().contains(assign)).values();

								if (!filtered.isEmpty()) {
									String full = '/' + filtered.iterator().next().getPathWithoutExtension().replace("FortniteGame/Content", "Game");

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
										case "Emissive":
											matTex[3] = full;
											break;
									}
								}
							}
						}

						entry1.set(4, JWPSerializer.GSON.toJsonTree(matTex));
					}
				}

			file = new File("processed.json");
			System.out.println("Writing to " + file.getAbsolutePath());

			try (FileWriter writer = new FileWriter(file)) {
				JWPSerializer.GSON.toJson(bruh, writer);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static Package loadIfNot(DefaultFileProvider provider, String pkg) {
		return loadIfNot(provider, provider.findGameFile(pkg));
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
			h(parts, "-path=\"" + GAME_PATH + '\"');
			parts.add("-game=ue4.24");
			parts.add("-aes=" + AES);
			h(parts, "-out=\"" + new File("").getAbsolutePath() + '\"');
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
		Map<String, GameFile> filtered = MapsKt.filter(provider.getFiles(), entry -> entry.getKey().endsWith(check));

		if (filtered.size() == 1) {
			return filtered.values().iterator().next();
		}

		filtered = MapsKt.filter(filtered, entry -> entry.getKey().contains("Actor".toLowerCase())); // keys are lower cased

		if (!filtered.isEmpty()) {
			GameFile out = filtered.values().iterator().next();

			if (filtered.size() > 1) {
				System.err.println("WANING: We've got 2 actors. We picked the first one: " + out);
			}

			return out;
		}

		System.err.println("WARNING: Actor not found: " + actorName);
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
		return getProp(((UObject) export).getProperties(), name, clazz);
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
}
