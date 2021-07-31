import bpy
from typing import List, Any, TypeVar
import os
import json

T = TypeVar("T")

def from_list(x: Any) -> List[T]:
    l = []
    l.append({
    "Guid": "00000000000000000000000000000000",
    "Key": bpy.context.scene.aeskey
    })
    for a in x:
        if a.pakname == "" and a.daeskey == "":
            continue
        if a.guid == "" and a.pakname == "":
            continue
        if a.daeskey == "":
            continue

        d = {}
        if a.guid != "":
            d["Guid"] = a.guid
        else:
            d["Pakname"] = a.pakname
        d["Key"] = a.daeskey
        l.append(d)
    return l

class Config:
    Documentation: str = "https://github.com/Amrsatrio/BlenderUmap/blob/master/README.md"
    PaksDirectory: str
    ExportPath: str
    UEVersion: str
    EncryptionKeys: List[Any]
    bDumpAssets: bool
    ObjectCacheSize: int
    bReadMaterials: bool
    bExportToDDSWhenPossible: bool
    bExportBuildingFoundations: bool
    bUseUModel: bool
    UModelAdditionalArgs: str
    ExportPackage: str

    def __init__(self) -> None:
        sc = bpy.context.scene
        self.PaksDirectory = sc.Game_Path[:-1]
        self.ExportPath = sc.exportPath
        self.UEVersion = sc.ue4_versions
        self.EncryptionKeys = sc.dpklist
        self.bDumpAssets = sc.bdumpassets
        self.ObjectCacheSize = sc.ObjectCacheSize
        self.bReadMaterials = sc.readmats
        self.bExportToDDSWhenPossible = sc.bExportToDDSWhenPossible
        self.bExportBuildingFoundations = sc.bExportBuildingFoundations
        self.bUseUModel = sc.bUseUModel
        self.UModelAdditionalArgs = sc.additionalargs
        self.ExportPackage = sc.package

    def to_dict(self) -> dict:
        result: dict = {}
        result["_Documentation"] = self.Documentation
        result["PaksDirectory"] = self.PaksDirectory
        result["ExportPath"] = self.ExportPath
        result["UEVersion"] = self.UEVersion
        result["bDumpAssets"] = self.bDumpAssets
        result["ObjectCacheSize"] = self.ObjectCacheSize
        result["bReadMaterials"] = self.bReadMaterials
        result["bExportToDDSWhenPossible"] = self.bExportToDDSWhenPossible
        result["bExportBuildingFoundations"] = self.bExportBuildingFoundations
        result["bUseUModel"] = self.bUseUModel
        result["UModelAdditionalArgs"] = self.UModelAdditionalArgs
        result["ExportPackage"] = self.ExportPackage
        result["EncryptionKeys"] = from_list(self.EncryptionKeys)
        return result

    def load(self, out = {}):
        if not os.path.exists(os.path.join(self.ExportPath,"config.json")):
            return
        with open(os.path.join(self.ExportPath,"config.json"),"r") as f:
            data = json.load(f)
            out = data

        sc = bpy.context.scene

        sc.Game_Path = data["PaksDirectory"] + "/"
        sc.exportPath = data["ExportPath"]
        sc.ue4_versions = data["UEVersion"]
        sc.bdumpassets = data["bDumpAssets"]
        sc.ObjectCacheSize = data["ObjectCacheSize"]
        sc.readmats = data["bReadMaterials"]
        sc.bExportToDDSWhenPossible = data["bExportToDDSWhenPossible"]
        sc.bExportBuildingFoundations = data["bExportBuildingFoundations"]
        sc.bUseUModel = data["bUseUModel"]
        sc.additionalargs = data["UModelAdditionalArgs"]
        sc.package = data["ExportPackage"]

        for a in range(len(sc.dpklist)):
            sc.dpklist.remove(a)

        sc.list_index = 0
        i = 0
        for x in data["EncryptionKeys"]:
            if guid := x.get("Guid"):
                if guid == "00000000000000000000000000000000":
                    sc.aeskey = x["Key"]
                    continue

            sc.list_index = i
            sc.dpklist.add()
            dpk = sc.dpklist[i]
            dpk.guid = x.get("Guid") or ""
            dpk.pakname = x.get("FileName") or ""
            dpk.daeskey = x["Key"]
            i += 1

    def dump(self,path):
        with open(os.path.join(path,"config.json"),"w") as f:
            json.dump(self.to_dict(),f,indent=4)
