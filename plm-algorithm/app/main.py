"""恒剑光电 PLM V4.0 算法微服务

负责 3D 模型解析、轻量化、爆炸 BOM 提取、AI 建模平台对接。
"""
import uvicorn
from fastapi import FastAPI

from app.api import ai_modeling, drawing, model3d
from app.core.config import settings

app = FastAPI(
    title="恒剑光电 PLM 算法微服务",
    description="3D模型解析、AI建模对接、BOM爆炸数据源、2D图纸分解",
    version="4.0.0",
)

app.include_router(model3d.router, prefix="/api")
app.include_router(ai_modeling.router, prefix="/api")
app.include_router(drawing.router, prefix="/api")


@app.get("/")
async def root():
    return {"service": "PLM Algorithm Service", "version": "4.0.0", "status": "running"}


@app.get("/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    uvicorn.run("app.main:app", host=settings.host, port=settings.port, reload=settings.debug)
