"""3D 格式转换服务

将 STEP/IGES/OBJ/STL 等格式转换为 GLB, 供 Three.js 在线预览。
- OBJ/STL/PLY: trimesh 直接转换
- STEP/IGES/BREP: gmsh (OpenCASCADE) 导入 + 网格化 → STL → trimesh → GLB
"""
import os
import tempfile
import trimesh

try:
    import gmsh
    GMSH_AVAILABLE = True
except ImportError:
    GMSH_AVAILABLE = False


class ModelConverter:
    """3D 模型格式转换器"""

    # trimesh 可直接处理的格式
    DIRECT_FORMATS = {".obj", ".stl", ".ply", ".glb", ".gltf", ".collada", ".dae"}
    # 需要 gmsh 中间转换的 CAD 格式
    CAD_FORMATS = {".step", ".stp", ".iges", ".igs", ".brep"}

    @classmethod
    def can_convert(cls, filename: str) -> bool:
        ext = cls._ext(filename)
        if ext in cls.DIRECT_FORMATS:
            return True
        if ext in cls.CAD_FORMATS and GMSH_AVAILABLE:
            return True
        return False

    @classmethod
    def get_conversion_method(cls, filename: str) -> str:
        ext = cls._ext(filename)
        if ext in cls.DIRECT_FORMATS:
            return "trimesh"
        if ext in cls.CAD_FORMATS:
            return "gmsh" if GMSH_AVAILABLE else "unsupported"
        return "unsupported"

    @classmethod
    def convert_to_glb(cls, input_path: str, output_path: str = None) -> dict:
        """将任意 3D 格式转换为 GLB

        Returns:
            {"success": bool, "output": str, "method": str, "info": dict}
        """
        ext = cls._ext(input_path)
        if output_path is None:
            output_path = input_path.rsplit(".", 1)[0] + ".glb"

        if ext in {".glb", ".gltf"}:
            return {"success": True, "output": input_path, "method": "noop",
                    "info": {"message": "Already GLB/glTF format"}}

        if ext in cls.DIRECT_FORMATS:
            return cls._convert_with_trimesh(input_path, output_path)

        if ext in cls.CAD_FORMATS:
            if not GMSH_AVAILABLE:
                return {"success": False, "output": None, "method": "none",
                        "info": {"error": "gmsh not installed, cannot convert CAD format"}}
            return cls._convert_cad_with_gmsh(input_path, output_path)

        return {"success": False, "output": None, "method": "none",
                "info": {"error": f"Unsupported format: {ext}"}}

    @classmethod
    def _convert_with_trimesh(cls, input_path: str, output_path: str) -> dict:
        """使用 trimesh 直接转换 (OBJ/STL/PLY 等)"""
        try:
            scene = trimesh.load(input_path, force="scene")
            if isinstance(scene, trimesh.Scene):
                if len(scene.geometry) == 0:
                    return cls._fail("No geometry found in file")
                merged = trimesh.util.concatenate([
                    g for g in scene.geometry.values() if isinstance(g, trimesh.Trimesh)
                ])
                if merged is None:
                    mesh = list(scene.geometry.values())[0]
                else:
                    mesh = merged
            else:
                mesh = scene

            if not isinstance(mesh, trimesh.Trimesh):
                mesh = trimesh.Trimesh(vertices=mesh.vertices, faces=mesh.faces)

            mesh.export(output_path, file_type="glb")
            info = {
                "vertices": len(mesh.vertices),
                "faces": len(mesh.faces),
                "bounds": mesh.bounds.tolist() if hasattr(mesh, "bounds") else None,
            }
            return {"success": True, "output": output_path, "method": "trimesh", "info": info}
        except Exception as e:
            return cls._fail(f"trimesh conversion failed: {e}")

    @classmethod
    def _convert_cad_with_gmsh(cls, input_path: str, output_path: str) -> dict:
        """使用 gmsh (OpenCASCADE) 转换 CAD 文件 (STEP/IGES)"""
        temp_stl = None
        try:
            temp_stl = tempfile.mktemp(suffix=".stl")
            gmsh.initialize()
            gmsh.option.setNumber("General.Terminal", 0)
            gmsh.option.setNumber("Mesh.MeshSizeMin", 1.0)
            gmsh.option.setNumber("Mesh.MeshSizeMax", 20.0)
            gmsh.option.setNumber("Mesh.Algorithm", 6)  # Frontal-Delaunay
            gmsh.merge(input_path)
            gmsh.model.mesh.generate(2)
            gmsh.write(temp_stl)
            gmsh.finalize()

            mesh = trimesh.load(temp_stl, force="mesh")
            if not isinstance(mesh, trimesh.Trimesh):
                mesh = trimesh.Trimesh(vertices=mesh.vertices, faces=mesh.faces)

            if len(mesh.faces) > 100000:
                target = 50000
                mesh = mesh.simplify_quadric_decimation(target)

            mesh.export(output_path, file_type="glb")
            info = {
                "vertices": len(mesh.vertices),
                "faces": len(mesh.faces),
                "original_format": cls._ext(input_path),
                "bounds": mesh.bounds.tolist() if hasattr(mesh, "bounds") else None,
            }
            return {"success": True, "output": output_path, "method": "gmsh+trimesh", "info": info}
        except Exception as e:
            return cls._fail(f"gmsh conversion failed: {e}")
        finally:
            try:
                if temp_stl and os.path.exists(temp_stl):
                    os.remove(temp_stl)
            except Exception:
                pass

    @classmethod
    def _convert_assembly_glb(cls, input_path: str, output_path: str) -> dict:
        """转换装配体并保留零件层级 (生成多个 mesh 的 GLB)"""
        try:
            scene = trimesh.load(input_path, force="scene")
            if not isinstance(scene, trimesh.Scene):
                return cls._convert_with_trimesh(input_path, output_path)

            geometries = {name: g for name, g in scene.geometry.items()
                          if isinstance(g, trimesh.Trimesh)}
            if not geometries:
                return cls._fail("No mesh geometries found")

            scene.export(output_path, file_type="glb")
            info = {
                "parts": len(geometries),
                "total_vertices": sum(len(g.vertices) for g in geometries.values()),
                "total_faces": sum(len(g.faces) for g in geometries.values()),
                "part_names": list(geometries.keys())[:20],
            }
            return {"success": True, "output": output_path, "method": "trimesh-scene", "info": info}
        except Exception as e:
            return cls._fail(f"Assembly conversion failed: {e}")

    @staticmethod
    def _ext(filename: str) -> str:
        return os.path.splitext(filename)[1].lower()

    @staticmethod
    def _fail(msg: str) -> dict:
        return {"success": False, "output": None, "method": "none", "info": {"error": msg}}
