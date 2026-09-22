"""AI 建模平台对接服务 (Meshy / Tripo3D)

预留对接接口，支持图片/文字描述生成 3D 模型。
"""
import httpx

from app.core.config import settings


class MeshyClient:
    """Meshy AI 建模平台客户端"""

    def __init__(self):
        self.base_url = settings.meshy_base_url
        self.api_key = settings.meshy_api_key
        self.headers = {"Authorization": f"Bearer {self.api_key}"} if self.api_key else {}

    async def text_to_3d(self, prompt: str, art_style: str = "realistic") -> dict:
        payload = {
            "mode": "text-to-3d",
            "prompt": prompt,
            "art_style": art_style,
            "negative_prompt": "low quality, blurry",
            "seed": 0,
            "topology": "quad",
            "target_polycount": 30000,
            "enable_pbr": True,
        }
        async with httpx.AsyncClient() as client:
            resp = await client.post(f"{self.base_url}/v2/text-to-3d", json=payload, headers=self.headers, timeout=60)
            resp.raise_for_status()
            return resp.json()

    async def image_to_3d(self, image_url: str) -> dict:
        payload = {
            "mode": "image-to-3d",
            "image_url": image_url,
            "topology": "quad",
            "target_polycount": 30000,
            "enable_pbr": True,
        }
        async with httpx.AsyncClient() as client:
            resp = await client.post(f"{self.base_url}/v2/image-to-3d", json=payload, headers=self.headers, timeout=60)
            resp.raise_for_status()
            return resp.json()

    async def get_task(self, task_id: str) -> dict:
        async with httpx.AsyncClient() as client:
            resp = await client.get(f"{self.base_url}/v2/text-to-3d/{task_id}", headers=self.headers, timeout=30)
            resp.raise_for_status()
            return resp.json()


class Tripo3DClient:
    """Tripo3D AI 建模平台客户端"""

    def __init__(self):
        self.base_url = settings.tripo3d_base_url
        self.api_key = settings.tripo3d_api_key
        self.headers = {"Authorization": f"Bearer {self.api_key}"} if self.api_key else {}

    async def text_to_model(self, prompt: str) -> dict:
        payload = {"type": "text_to_model", "prompt": prompt}
        async with httpx.AsyncClient() as client:
            resp = await client.post(f"{self.base_url}/task", json=payload, headers=self.headers, timeout=60)
            resp.raise_for_status()
            return resp.json()

    async def get_task(self, task_id: str) -> dict:
        async with httpx.AsyncClient() as client:
            resp = await client.get(f"{self.base_url}/task/{task_id}", headers=self.headers, timeout=30)
            resp.raise_for_status()
            return resp.json()


meshy_client = MeshyClient()
tripo3d_client = Tripo3DClient()
