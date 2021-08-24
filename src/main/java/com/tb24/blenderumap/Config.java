package com.tb24.blenderumap;

import me.fungames.jfortniteparse.ue4.versions.Ue4Version;

import java.util.Collections;
import java.util.List;

public class Config {
    public String PaksDirectory = "C:\\Program Files\\Epic Games\\Fortnite\\FortniteGame\\Content\\Paks";
    public Ue4Version UEVersion = Ue4Version.GAME_UE4_LATEST;
    public List<MyFileProvider.EncryptionKey> EncryptionKeys = Collections.emptyList();
    public boolean bDumpAssets = false;
    public int ObjectCacheSize = 100;
    public boolean bReadMaterials = true;
    public boolean bExportToDDSWhenPossible = true;
    public boolean bExportBuildingFoundations = true;
    public String ExportPackage;
}