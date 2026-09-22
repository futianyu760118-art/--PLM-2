"""Python 算法服务单元测试"""
import os
import sys
import pytest
import tempfile

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from app.services.model_parser import Model3DParser
from app.services.drawing_decomposer import DrawingDecomposer, CATEGORY_RULES


class TestModel3DParser:
    """3D 模型解析器测试"""

    def test_validate_format_step(self):
        assert Model3DParser.validate_format("test.step") is True
        assert Model3DParser.validate_format("test.STEP") is True

    def test_validate_format_stl(self):
        assert Model3DParser.validate_format("model.stl") is True

    def test_validate_format_invalid(self):
        assert Model3DParser.validate_format("test.pdf") is False
        assert Model3DParser.validate_format("test.docx") is False
        assert Model3DParser.validate_format("test.txt") is False

    def test_parse_nonexistent_file(self):
        with pytest.raises(FileNotFoundError):
            Model3DParser.parse_assembly("/nonexistent/file.step")

    def test_guess_material_complex(self):
        result = Model3DParser._guess_material(volume=60000, face_count=60000)
        assert "复杂曲面" in result or "结构件" in result

    def test_guess_material_small(self):
        result = Model3DParser._guess_material(volume=50, face_count=100)
        assert "小零件" in result or "标准件" in result


class TestDrawingDecomposer:
    """2D 图纸分解器测试"""

    def test_validate_format_dxf(self):
        assert DrawingDecomposer.validate_format("test.dxf") is True

    def test_validate_format_dwg(self):
        assert DrawingDecomposer.validate_format("test.dwg") is True

    def test_validate_format_invalid(self):
        assert DrawingDecomposer.validate_format("test.pdf") is False

    def test_category_rules_count(self):
        assert len(CATEGORY_RULES) == 8, "应有8类标准图纸分类"

    def test_category_rules_have_required_keys(self):
        required_cats = {"ASSEMBLY", "CNC", "EDM", "WIRE_CUT", "COOLING", "VENTING", "MOLD_BASE", "PARTS"}
        assert required_cats == set(CATEGORY_RULES.keys())

    def test_classify_cnc_layer(self):
        result = DrawingDecomposer._classify_layer(
            layer="CNC_CAVITY",
            entities=[],
            texts=["模仁加工"],
        )
        assert result == "CNC"

    def test_classify_cooling_layer(self):
        result = DrawingDecomposer._classify_layer(
            layer="COOLING_LINE",
            entities=[],
            texts=["水路运水"],
        )
        assert result == "COOLING"

    def test_classify_edm_by_text(self):
        result = DrawingDecomposer._classify_layer(
            layer="0",
            entities=[],
            texts=["EDM铜公放电"],
        )
        assert result == "EDM"

    def test_classify_unknown_falls_to_parts(self):
        result = DrawingDecomposer._classify_layer(
            layer="UNKNOWN_LAYER",
            entities=[],
            texts=["无关键词"],
        )
        assert result == "PARTS"


class TestFastAPIApp:
    """FastAPI 应用结构测试"""

    def test_app_import(self):
        from app.main import app
        assert app is not None

    def test_routes_registered(self):
        from app.main import app
        routes = [r.path for r in app.routes]
        assert "/health" in routes
        assert "/api/model3d/parse" in routes
        assert "/api/drawing/decompose" in routes
        assert "/api/ai-modeling/text-to-3d" in routes
