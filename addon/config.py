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
        l.append({
            "FileName" : a.pakname,
            "Key" : a.daeskey
        })
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
        result["EncryptionKeys"] = from_list(self.EncryptionKeys)
        result["bDumpAssets"] = self.bDumpAssets
        result["ObjectCacheSize"] = self.ObjectCacheSize
        result["bReadMaterials"] = self.bReadMaterials
        result["bExportToDDSWhenPossible"] = self.bExportToDDSWhenPossible
        result["bExportBuildingFoundations"] = self.bExportBuildingFoundations
        result["bUseUModel"] = self.bUseUModel
        result["UModelAdditionalArgs"] = self.UModelAdditionalArgs
        result["ExportPackage"] = self.ExportPackage
        return result

    def dump(self,path):
        with open(os.path.join(path,"config.json"),"w") as f:
            json.dump(self.to_dict(),f,indent=4)