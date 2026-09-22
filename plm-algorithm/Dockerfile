FROM python:3.11-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8001
ENV PLM_SETTINGS_UPLOAD_DIR=/data/files \
    PLM_SETTINGS_TEMP_DIR=/data/files/temp
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
