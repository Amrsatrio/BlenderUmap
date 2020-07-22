package com.tb24.blenderumap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import kotlin.collections.MapsKt;
import me.fungames.jfortniteparse.fileprovider.DefaultFileProvider;
import me.fungames.jfortniteparse.ue4.FGuid;
import me.fungames.jfortniteparse.ue4.assets.Package;
import me.fungames.jfortniteparse.ue4.assets.exports.UExport;
import me.fungames.jfortniteparse.ue4.assets.objects.FObjectExport;
import me.fungames.jfortniteparse.ue4.assets.objects.FObjectImport;
import me.fungames.jfortniteparse.ue4.assets.objects.FPackageIndex;
import me.fungames.jfortniteparse.ue4.pak.GameFile;
import me.fungames.jfortniteparse.ue4.pak.PakFileReader;
import me.fungames.jfortniteparse.ue4.versions.Ue4Version;

import static com.tb24.blenderumap.JWPSerializer.GSON;

public class MyFileProvider extends DefaultFileProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger("FileProvider");
	public static final File JSONS_FOLDER = new File("jsons");
	private final boolean bDumpAssets;
	private final Map<GameFile, Package> loaded = new HashMap<>();

	public MyFileProvider(File folder, Ue4Version game, Iterable<EncryptionKey> encryptionKeys, boolean bDumpAssets) {
		super(folder, game);
		this.bDumpAssets = bDumpAssets;

		Map<FGuid, byte[]> keysToSubmit = new HashMap<>();

		for (EncryptionKey entry : encryptionKeys) {
			if (entry.FileName != null && !entry.FileName.isEmpty()) {
				keysToSubmit.put(entry.Guid, entry.Key);
			} else {
				Optional<PakFileReader> foundGuid = getUnloadedPaks().stream().filter(it -> it.getFileName().equals(entry.FileName)).findFirst();

				if (foundGuid.isPresent()) {
					keysToSubmit.put(foundGuid.get().getPakInfo().getEncryptionKeyGuid(), entry.Key);
				} else {
					LOGGER.warn("PAK file not found: " + entry.FileName);
				}
			}
		}

		submitKeys(keysToSubmit);
	}

	public Package loadIfNot(String pkg) {
		GameFile gameFile = findGameFile(pkg);

		if (gameFile != null) {
			return loadIfNot(gameFile);
		} else {
			LOGGER.warn("Package " + pkg + " not found");
			return null;
		}
	}

	public Package loadIfNot(GameFile pkg) {
		return MapsKt.getOrPut(loaded, pkg, () -> {
			LOGGER.info("Loading " + pkg);
			Package loadedPkg = loadGameFile(pkg);

			if (loadedPkg != null && bDumpAssets) {
				File file = new File(JSONS_FOLDER, pkg.getPathWithoutExtension() + ".json");
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

	public UExport loadObjectPath(String objectPath) {
		if (objectPath == null) return null;

		int dotIndex = objectPath.lastIndexOf('.');
		String pkgPath, objectName;

		if (dotIndex != -1) {
			pkgPath = objectPath.substring(0, dotIndex);
			objectName = objectPath.substring(dotIndex + 1);
		} else {
			pkgPath = objectPath;
			objectName = objectPath.substring(objectPath.lastIndexOf('/') + 1);
		}

		Package pkg = loadIfNot(pkgPath);

		for (FObjectExport export : pkg.getExportMap()) {
			if (export.getObjectName().getText().equals(objectName)) {
				return export.getExportObject().getValue();
			}
		}

		return null;
	}

	public UExport loadObject(FPackageIndex index) {
		if (index == null) return null;

		Package owner = index.getOwner();
		int i = index.getIndex();

		if (i < 0) { // import
			FObjectImport imp = owner.getImportMap().get(-i - 1);
			Package pkg = loadIfNot(owner.getImportMap().get(-imp.getOuterIndex().getIndex() - 1).getObjectName().getText());

			if (pkg != null) {
				for (FObjectExport export : pkg.getExportMap()) {
					if (export.getClassIndex().getName().equals(imp.getClassName().getText()) && export.getObjectName().getText().equals(imp.getObjectName().getText())) {
						return export.getExportObject().getValue();
					}
				}
			}
		} else if (i > 0) { // export
			return owner.getExportMap().get(i - 1).getExportObject().getValue();
		}

		return null;
	}

	public static String compactFilePath(String path) {
		if (path.charAt(0) == '/') {
			return path;
		}

		if (path.startsWith("Engine/Content")) { // -> /Engine
			return "/Engine" + path.substring("Engine/Content".length());
		}

		if (path.startsWith("Engine/Plugins")) { // -> /Plugins
			return path.substring("Engine".length());
		}

		int delim = path.indexOf("/Content/");

		if (delim == -1) {
			return path;
		}

		// GameName/Content -> /Game
		return "/Game" + path.substring(delim + "/Content".length());
	}

	public static class EncryptionKey {
		public FGuid Guid;
		public String FileName;
		public byte[] Key;

		public EncryptionKey() {
			Guid = FGuid.Companion.getMainGuid();
			Key = new byte[]{};
		}

		public EncryptionKey(FGuid guid, byte[] key) {
			Guid = guid;
			Key = key;
		}
	}
}
