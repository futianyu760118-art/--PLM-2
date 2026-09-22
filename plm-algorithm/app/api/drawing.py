"""2D 图纸自动分解接口"""
import os
import shutil

from fastapi import APIRouter, File, UploadFile, HTTPException

from app.core.config import settings
from app.services.drawing_decomposer import DrawingDecomposer

router = APIRouter(prefix="/drawing", tags=["2D图纸分解"])


@router.post("/decompose")
async def decompose_drawing(file: UploadFile = File(...)):
    """上传模具总装 DWG/DXF, 自动分解为 8 类标准化加工图纸

    分类: 总装图/CNC模仁/EDM铜公/线割/水路/排气槽/模胚/散件
    """
    if not DrawingDecomposer.validate_format(file.filename):
        raise HTTPException(status_code=400, detail="仅支持 DXF/DWG 格式")

    os.makedirs(settings.temp_dir, exist_ok=True)
    tmp_path = os.path.join(settings.temp_dir, file.filename)
    with open(tmp_path, "wb") as f:
        shutil.copyfileobj(file.file, f)
    try:
        result = DrawingDecomposer.decompose(tmp_path)
        if "error" in result:
            raise HTTPException(status_code=422, detail=result["error"])
        return {
            "code": 200,
            "message": f"分解完成: {result['classified_count']} 张图纸, {len(result['classification_summary'])} 个分类",
            "data": result,
        }
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


@router.get("/categories")
async def categories():
    """返回标准图纸分类(8类)"""
    from app.services.drawing_decomposer import CATEGORY_RULES
    return {
        "code": 200,
        "data": [
            {"code": k, "name": v["name"]}
            for k, v in CATEGORY_RULES.items()
        ],
    }
