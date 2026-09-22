"""3D 模型解析接口"""
import os
import shutil
import tempfile
from typing import Optional

from fastapi import APIRouter, File, UploadFile, HTTPException, BackgroundTasks
from fastapi.responses import FileResponse

from app.core.config import settings
from app.services.model_parser import Model3DParser
from app.services.model_converter import ModelConverter

router = APIRouter(prefix="/model3d", tags=["3D模型解析"])


@router.post("/parse")
async def parse_model(file: UploadFile = File(...)):
    """上传 3D 装配模型并解析零件结构 (生成初级爆炸 BOM 数据源)"""
    if not Model3DParser.validate_format(file.filename):
        raise HTTPException(status_code=400, detail="不支持的文件格式,支持 STEP/IGS/OBJ/STL/GLB")

    os.makedirs(settings.temp_dir, exist_ok=True)
    tmp_path = os.path.join(settings.temp_dir, file.filename)
    with open(tmp_path, "wb") as f:
        shutil.copyfileobj(file.file, f)
    try:
        result = Model3DParser.parse_assembly(tmp_path)
        return {"code": 200, "message": "解析成功", "data": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"模型解析失败: {e}")
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


@router.post("/simplify")
async def simplify_model(
    file: UploadFile = File(...),
    ratio: float = 0.5,
):
    """网格轻量化，导出 GLB 供前端 Three.js 预览"""
    if not Model3DParser.validate_format(file.filename):
        raise HTTPException(status_code=400, detail="不支持的文件格式")
    os.makedirs(settings.temp_dir, exist_ok=True)
    base = os.path.splitext(file.filename)[0]
    tmp_in = os.path.join(settings.temp_dir, file.filename)
    tmp_out = os.path.join(settings.temp_dir, f"{base}_simplified.glb")
    with open(tmp_in, "wb") as f:
        shutil.copyfileobj(file.file, f)
    try:
        Model3DParser.simplify_mesh(tmp_in, tmp_out, ratio)
        size = os.path.getsize(tmp_out)
        return {"code": 200, "message": "轻量化成功", "data": {
            "output": tmp_out,
            "format": "glb",
            "size": size,
            "ratio": ratio,
        }}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"轻量化失败: {e}")


@router.get("/health")
async def health():
    return {"code": 200, "message": "ok", "service": "model3d"}


@router.post("/convert-to-glb")
async def convert_to_glb(file: UploadFile = File(...)):
    """将 STEP/IGES/OBJ/STL 等格式转换为 GLB (供 Three.js 在线预览)

    - OBJ/STL/PLY: trimesh 直接转换
    - STEP/IGES/BREP: gmsh(OpenCASCADE) 网格化 → GLB
    """
    if not ModelConverter.can_convert(file.filename):
        method = ModelConverter.get_conversion_method(file.filename)
        raise HTTPException(status_code=400,
            detail=f"不支持的格式: {file.filename} (转换方式: {method})")

    os.makedirs(settings.temp_dir, exist_ok=True)
    base = os.path.splitext(file.filename)[0]
    tmp_in = os.path.join(settings.temp_dir, file.filename)
    tmp_out = os.path.join(settings.temp_dir, f"{base}_converted.glb")
    with open(tmp_in, "wb") as f:
        shutil.copyfileobj(file.file, f)
    try:
        result = ModelConverter.convert_to_glb(tmp_in, tmp_out)
        if not result["success"]:
            raise HTTPException(status_code=422, detail=result["info"].get("error", "转换失败"))
        return {
            "code": 200,
            "message": f"转换成功 ({result['method']}): {result['info'].get('faces', 0)} 面数",
            "data": {
                "method": result["method"],
                "info": result["info"],
                "filename": f"{base}.glb",
            }
        }
    finally:
        if os.path.exists(tmp_in):
            os.remove(tmp_in)


@router.post("/convert-and-download")
async def convert_and_download(file: UploadFile = File(...)):
    """转换并直接下载 GLB 文件"""
    if not ModelConverter.can_convert(file.filename):
        raise HTTPException(status_code=400, detail=f"不支持的格式")
    os.makedirs(settings.temp_dir, exist_ok=True)
    base = os.path.splitext(file.filename)[0]
    tmp_in = os.path.join(settings.temp_dir, file.filename)
    tmp_out = os.path.join(settings.temp_dir, f"{base}.glb")
    with open(tmp_in, "wb") as f:
        shutil.copyfileobj(file.file, f)
    try:
        result = ModelConverter.convert_to_glb(tmp_in, tmp_out)
        if not result["success"]:
            raise HTTPException(status_code=422, detail=result["info"].get("error", "转换失败"))
        return FileResponse(
            path=tmp_out,
            media_type="model/gltf-binary",
            filename=f"{base}.glb"
        )
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
