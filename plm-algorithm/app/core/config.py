"""恒剑光电 PLM V4.0 算法微服务配置"""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # 服务配置
    app_name: str = "PLM Algorithm Service"
    host: str = "0.0.0.0"
    port: int = 8001
    debug: bool = True

    # AI 建模平台 API (预留)
    meshy_api_key: str = ""
    meshy_base_url: str = "https://api.meshy.ai"
    tripo3d_api_key: str = ""
    tripo3d_base_url: str = "https://api.tripo3d.ai/v2"

    # 文件存储
    upload_dir: str = "D:/PLM-2/files/intranet"
    temp_dir: str = "D:/PLM-2/files/temp"

    # 主后端回调
    backend_base_url: str = "http://localhost:8080/api"

    # 3D 模型轻量化参数
    simplify_ratio: float = 0.5  # 面数精简比例
    max_texture_size: int = 2048

    class Config:
        env_file = ".env"
        env_prefix = "PLM_"


settings = Settings()
