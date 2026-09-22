"""3D 模型解析服务

负责 STEP/IGS/SLDPRT 等装配文件的解析、轻量化、爆炸 BOM 提取。
通过 trimesh 库实现网格解析，零件信息提取。
"""
import os
from dataclasses import dataclass
from typing import List, Optional

import trimesh


@dataclass
class ParsedPart:
    """解析出的零件信息"""
    name: str
    volume: float
    mass: Optional[float]
    bounding_box: List[float]
    face_count: int
    vertex_count: int
    material_guess: Optional[str]


class Model3DParser:
    """3D 装配模型解析器"""

    SUPPORTED_FORMATS = {".step", ".stp", ".igs", ".iges", ".obj", ".stl", ".glb", ".gltf"}

    @classmethod
    def validate_format(cls, filename: str) -> bool:
        ext = os.path.splitext(filename)[1].lower()
        return ext in cls.SUPPORTED_FORMATS

    @classmethod
    def parse_assembly(cls, file_path: str) -> dict:
        """解析装配体，返回爆炸 BOM 初级数据

        Returns:
            {
                "parts": [ParsedPart],
                "total_parts": int,
                "scene_info": {...}
            }
        """
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"模型文件不存在: {file_path}")

        scene = trimesh.load(file_path, force="scene")
        parts: List[ParsedPart] = []

        if isinstance(scene, trimesh.Scene):
            for name, geometry in scene.geometry.items():
                part = cls._extract_part(name, geometry)
                parts.append(part)
        else:
            part = cls._extract_part("root", scene)
            parts.append(part)

        return {
            "parts": [p.__dict__ for p in parts],
            "total_parts": len(parts),
            "scene_info": {
                "format": os.path.splitext(file_path)[1],
                "has_multiple_parts": len(parts) > 1,
            },
        }

    @classmethod
    def _extract_part(cls, name: str, geometry) -> ParsedPart:
        volume = float(getattr(geometry, "volume", 0.0))
        face_count = int(getattr(geometry, "faces", []).shape[0]) if hasattr(geometry, "faces") else 0
        vertex_count = int(getattr(geometry, "vertices", []).shape[0]) if hasattr(geometry, "vertices") else 0
        bbox = []
        if hasattr(geometry, "bounding_box"):
            bbox = geometry.bounding_box.extents.tolist()
        return ParsedPart(
            name=name,
            volume=round(volume, 4),
            mass=None,
            bounding_box=[round(v, 4) for v in bbox],
            face_count=face_count,
            vertex_count=vertex_count,
            material_guess=cls._guess_material(volume, face_count),
        )

    @staticmethod
    def _guess_material(volume: float, face_count: int) -> Optional[str]:
        if face_count > 50000:
            return "复杂曲面/模具"
        if volume > 50000:
            return "结构件"
        if volume < 100:
            return "小零件/标准件"
        return "常规件"

    @classmethod
    def simplify_mesh(cls, file_path: str, output_path: str, ratio: float = 0.5) -> str:
        """网格轻量化，输出 GLB 供前端预览"""
        mesh = trimesh.load(file_path, force="mesh")
        target_faces = int(len(mesh.faces) * ratio)
        simplified = mesh.simplify_quadric_decimation(target_faces)
        simplified.export(output_path)
        return output_path
