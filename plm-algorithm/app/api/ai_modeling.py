"""AI 建模接口 (Meshy / Tripo3D)"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.services.ai_modeling import meshy_client, tripo3d_client

router = APIRouter(prefix="/ai-modeling", tags=["AI建模"])


class TextTo3DRequest(BaseModel):
    prompt: str
    platform: str = "meshy"  # meshy / tripo3d
    art_style: str = "realistic"


class ImageTo3DRequest(BaseModel):
    image_url: str
    platform: str = "meshy"


@router.post("/text-to-3d")
async def text_to_3d(req: TextTo3DRequest):
    """文字描述生成 3D 模型"""
    try:
        if req.platform == "tripo3d":
            result = await tripo3d_client.text_to_model(req.prompt)
        else:
            result = await meshy_client.text_to_3d(req.prompt, req.art_style)
        return {"code": 200, "message": "任务已提交", "data": result}
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"AI建模平台调用失败: {e}")


@router.post("/image-to-3d")
async def image_to_3d(req: ImageTo3DRequest):
    """图片生成 3D 模型"""
    try:
        result = await meshy_client.image_to_3d(req.image_url)
        return {"code": 200, "message": "任务已提交", "data": result}
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"AI建模平台调用失败: {e}")


@router.get("/task/{platform}/{task_id}")
async def get_task(platform: str, task_id: str):
    """查询 AI 建模任务状态"""
    try:
        if platform == "tripo3d":
            result = await tripo3d_client.get_task(task_id)
        else:
            result = await meshy_client.get_task(task_id)
        return {"code": 200, "data": result}
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"查询失败: {e}")
