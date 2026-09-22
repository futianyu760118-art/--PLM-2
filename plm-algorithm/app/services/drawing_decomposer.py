"""2D 模具加工图纸自动分解引擎

上传模具总装 DWG/DXF 文件,系统自动解析图层/实体,分类归档为:
1. 模具总装开模图
2. CNC 模仁加工图
3. EDM 铜公放电图纸
4. 线割加工图
5. 模具水路运水图
6. 排气槽/薄骨位标注图
7. 模胚加工图
8. 单件零件散件加工图

基于 ezdxf 解析 DXF 文件的图层、实体类型、文本标注进行智能分类。
"""
import re
from dataclasses import dataclass, field
from typing import List, Dict, Optional

import ezdxf
from ezdxf.entities import DXFGraphic


@dataclass
class DrawingSheet:
    """分解出的一张图纸"""
    category: str          # 分类编码
    category_name: str     # 分类名称
    layer: str             # 来源图层
    entity_count: int      # 实体数量
    text_labels: List[str] # 文本标注
    has_dimension: bool    # 含尺寸标注
    has_tolerance: bool    # 含公差
    bbox: List[float]      # 边界框


@dataclass
class DecomposeResult:
    """图纸分解结果"""
    source_file: str
    total_entities: int
    total_layers: int
    sheets: List[DrawingSheet] = field(default_factory=list)
    classification_summary: Dict[str, int] = field(default_factory=dict)


# 分类规则: 关键词 -> 分类
CATEGORY_RULES = {
    "ASSEMBLY": {
        "name": "01_模具总装开模图",
        "layer_keywords": ["ASSEMBLY", "总装", "装配", "MOLD_ASSY", "开模"],
        "text_keywords": ["总装", "开模", "拔模角", "缩水", "SHRINK", "DRAFT"],
        "entity_types": {"INSERT"},
    },
    "CNC": {
        "name": "02_CNC模仁加工图",
        "layer_keywords": ["CNC", "模仁", "CAVITY", "CORE", "前模", "后模", "MACHINING"],
        "text_keywords": ["CNC", "模仁", "前模", "后模", "加工", "RA", "表面粗糙度"],
        "entity_types": {"CIRCLE", "ARC", "LWPOLYLINE", "LINE"},
    },
    "EDM": {
        "name": "03_EDM铜公放电线+电极清单",
        "layer_keywords": ["EDM", "铜公", "电极", "放电", "ELECTRODE"],
        "text_keywords": ["EDM", "铜公", "电极", "放电", "火花"],
        "entity_types": set(),
    },
    "WIRE_CUT": {
        "name": "04_线割加工图",
        "layer_keywords": ["WIRE", "线割", "WEDM", "斜顶", "行位", "SLIDER", "LIFTER"],
        "text_keywords": ["线割", "WIRE", "斜顶", "行位", "镶件"],
        "entity_types": {"LWPOLYLINE", "LINE"},
    },
    "COOLING": {
        "name": "05_模具水路运水图",
        "layer_keywords": ["COOLING", "水路", "运水", "WATER", "CHANNEL"],
        "text_keywords": ["水路", "运水", "冷却", "WATER", "COOLING", "水管"],
        "entity_types": {"CIRCLE", "LINE"},
    },
    "VENTING": {
        "name": "06_排气槽薄骨位标注图",
        "layer_keywords": ["VENT", "排气", "薄骨", "VENTING"],
        "text_keywords": ["排气", "薄骨", "VENT", "骨位"],
        "entity_types": set(),
    },
    "MOLD_BASE": {
        "name": "07_模胚加工图",
        "layer_keywords": ["MOLD_BASE", "模胚", "模架", "BASE", "PLATE"],
        "text_keywords": ["模胚", "模架", "A板", "B板", "PLATE"],
        "entity_types": set(),
    },
    "PARTS": {
        "name": "08_单件零件散件加工图",
        "layer_keywords": ["PART", "零件", "散件", "INSERT"],
        "text_keywords": ["零件", "散件", "PART"],
        "entity_types": set(),
    },
}


class DrawingDecomposer:
    """2D 图纸自动分解引擎"""

    SUPPORTED_FORMATS = {".dxf", ".dwg"}

    @classmethod
    def validate_format(cls, filename: str) -> bool:
        ext = filename.lower().rsplit(".", 1)[-1] if "." in filename else ""
        return f".{ext}" in cls.SUPPORTED_FORMATS

    @classmethod
    def decompose(cls, file_path: str) -> dict:
        """分解 DWG/DXF 文件,返回分类图纸清单"""
        if not cls.validate_format(file_path):
            return {"error": "仅支持 DXF/DWG 格式"}

        try:
            doc = ezdxf.readfile(file_path)
        except Exception as e:
            if file_path.lower().endswith(".dwg"):
                return {
                    "error": "DWG 需先转换为 DXF (oddafterconverter). 请上传 DXF 格式,或通过 ODA Converter 预处理",
                    "detail": str(e),
                }
            return {"error": f"文件解析失败: {e}"}

        msp = doc.modelspace()
        result = DecomposeResult(source_file=file_path, total_entities=0, total_layers=0)

        layer_entities: Dict[str, List[DXFGraphic]] = {}
        layer_texts: Dict[str, List[str]] = {}
        for entity in msp:
            result.total_entities += 1
            layer = entity.dxf.layer if hasattr(entity.dxf, "layer") else "0"
            layer_entities.setdefault(layer, []).append(entity)
            if entity.dxftype() == "TEXT" or entity.dxftype() == "MTEXT":
                text = entity.dxf.text if entity.dxftype() == "TEXT" else entity.text
                layer_texts.setdefault(layer, []).append(text)

        result.total_layers = len(layer_entities)

        for layer, entities in layer_entities.items():
            texts = layer_texts.get(layer, [])
            category = cls._classify_layer(layer, entities, texts)
            if category is None:
                continue
            rule = CATEGORY_RULES[category]
            has_dim = any(e.dxftype() == "DIMENSION" for e in entities)
            has_tol = any(re.search(r"±|公差|TOL|H7|h6|g6", t, re.IGNORECASE) for t in texts)
            sheet = DrawingSheet(
                category=category,
                category_name=rule["name"],
                layer=layer,
                entity_count=len(entities),
                text_labels=texts[:5],
                has_dimension=has_dim,
                has_tolerance=has_tol,
                bbox=cls._bbox(entities),
            )
            result.sheets.append(sheet)

        for s in result.sheets:
            result.classification_summary[s.category_name] = result.classification_summary.get(s.category_name, 0) + 1

        return {
            "source_file": result.source_file,
            "total_entities": result.total_entities,
            "total_layers": result.total_layers,
            "sheets": [s.__dict__ for s in result.sheets],
            "classification_summary": result.classification_summary,
            "classified_count": len(result.sheets),
        }

    @classmethod
    def _classify_layer(cls, layer: str, entities: List[DXFGraphic], texts: List[str]) -> Optional[str]:
        layer_upper = layer.upper()
        all_text = " ".join(texts).upper()
        entity_types = {e.dxftype() for e in entities}
        scores = {}
        for cat, rule in CATEGORY_RULES.items():
            score = 0
            for kw in rule["layer_keywords"]:
                if kw.upper() in layer_upper:
                    score += 3
            for kw in rule["text_keywords"]:
                if kw.upper() in all_text:
                    score += 2
            if rule["entity_types"] and rule["entity_types"] & entity_types:
                score += 1
            if score > 0:
                scores[cat] = score
        if not scores:
            return "PARTS"
        return max(scores, key=scores.get)

    @classmethod
    def _bbox(cls, entities: List[DXFGraphic]) -> List[float]:
        xs, ys = [], []
        for e in entities:
            try:
                if hasattr(e, "dxf") and hasattr(e.dxf, "start") and hasattr(e.dxf, "end"):
                    xs.extend([e.dxf.start[0], e.dxf.end[0]])
                    ys.extend([e.dxf.start[1], e.dxf.end[1]])
                elif hasattr(e, "get_points"):
                    for p in e.get_points():
                        xs.append(p[0]); ys.append(p[1])
            except Exception:
                continue
        if not xs or not ys:
            return [0, 0, 0, 0]
        return [min(xs), min(ys), max(xs), max(ys)]
