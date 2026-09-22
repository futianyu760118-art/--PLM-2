package com.hjgd.plm.file.watermark;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 水印引擎核心
 * 支持类型: PDF / JPG / PNG
 * 双模式: 显性水印(企业+外协+日期+有效期) + 隐形溯源水印(账号+IP)
 */
@Slf4j
@Component
public class WatermarkEngine {

    private static final String FONT_PATH = "C:/Windows/Fonts/simhei.ttf";

    /**
     * 自动判断文件类型并添加水印
     */
    public File apply(File source, File output, WatermarkConfig config) throws IOException {
        String name = source.getName().toLowerCase();
        if (name.endsWith(".pdf")) {
            return watermarkPdf(source, output, config);
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
            return watermarkImage(source, output, config);
        }
        log.warn("暂不支持的文件类型,直接复制: {}", name);
        copyFile(source, output);
        return output;
    }

    /**
     * PDF 添加水印 (全页平铺 + 隐形元数据)
     */
    public File watermarkPdf(File source, File output, WatermarkConfig config) throws IOException {
        try (PdfReader reader = new PdfReader(source);
             PdfWriter writer = new PdfWriter(output);
             PdfDocument pdfDoc = new PdfDocument(reader, writer)) {

            PdfFont font = createChineseFont();
            PdfExtGState transparent = new PdfExtGState().setFillOpacity(config.getOpacity());
            DeviceRgb waterColor = new DeviceRgb(180, 180, 180);

            int pages = pdfDoc.getNumberOfPages();
            for (int i = 1; i <= pages; i++) {
                PdfPage page = pdfDoc.getPage(i);
                Rectangle pageSize = page.getPageSize();
                PdfCanvas canvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdfDoc);
                canvas.saveState();
                canvas.setExtGState(transparent);
                canvas.setFillColor(waterColor);

                float pageWidth = pageSize.getWidth();
                float pageHeight = pageSize.getHeight();
                String visible = config.visibleText();
                String invisible = config.invisibleText();

                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 4; col++) {
                        float x = col * (pageWidth / 3) - 40 + (row % 2) * 60;
                        float y = row * (pageHeight / 5) + 20;
                        Canvas cv = new Canvas(canvas, new Rectangle(x, y, 240, 60));
                        cv.setFont(font).setFontColor(waterColor).setFontSize(config.getFontSize() - 6);
                        cv.showTextAligned(visible, x + 120, y + 20, TextAlignment.CENTER, (float) Math.toRadians(config.getAngle()));
                        cv.close();
                    }
                }
                Canvas corner = new Canvas(canvas, new Rectangle(5, 5, 300, 14));
                corner.setFont(font).setFontColor(new DeviceRgb(200, 200, 200)).setFontSize(5);
                corner.showTextAligned(invisible, 10, 8, TextAlignment.LEFT);
                corner.close();

                canvas.restoreState();
            }
            pdfDoc.getDocumentInfo().setMoreInfo("HJ_TRACE", config.invisibleText());
            pdfDoc.getDocumentInfo().setMoreInfo("HJ_OUTSOURCE", config.getOutsourceCompany());
        } catch (Exception e) {
            log.error("PDF水印生成失败", e);
            throw new IOException("PDF水印生成失败", e);
        }
        log.info("PDF水印生成完成: {} (显性+隐形溯源)", output.getName());
        return output;
    }

    /**
     * 图片添加水印 (JPG/PNG)
     */
    public File watermarkImage(File source, File output, WatermarkConfig config) throws IOException {
        BufferedImage image = ImageIO.read(source);
        if (image == null) {
            copyFile(source, output);
            return output;
        }
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = image.getWidth();
        int height = image.getHeight();
        int fontSize = Math.max(18, width / 30);
        Font font = new Font("微软雅黑", Font.BOLD, fontSize);
        g2d.setFont(font);
        g2d.setColor(new Color(180, 180, 180, (int)(config.getOpacity() * 255)));

        String visible = config.visibleText();
        FontMetrics fm = g2d.getFontMetrics();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 5; col++) {
                int x = col * (width / 4) - 60;
                int y = row * (height / 7) + 30;
                g2d.rotate(Math.toRadians(config.getAngle()), x, y);
                g2d.drawString(visible, x, y);
                g2d.rotate(-Math.toRadians(config.getAngle()), x, y);
            }
        }
        g2d.setColor(new Color(200, 200, 200, 80));
        g2d.setFont(new Font("Arial", Font.PLAIN, 8));
        g2d.drawString(config.invisibleText(), 5, height - 5);

        g2d.dispose();
        String ext = source.getName().toLowerCase().endsWith(".png") ? "png" : "jpg";
        ImageIO.write(image, ext, output);
        log.info("图片水印生成完成: {} ({}x{})", output.getName(), width, height);
        return output;
    }

    private PdfFont createChineseFont() {
        try {
            File fontFile = new File(FONT_PATH);
            if (fontFile.exists()) {
                return PdfFontFactory.createFont(FONT_PATH, PdfEncodings.IDENTITY_H);
            }
            return PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
        } catch (Exception e) {
            log.warn("中文字体加载失败,使用默认字体: {}", e.getMessage());
        }
        try {
            return PdfFontFactory.createFont();
        } catch (IOException ex) {
            throw new RuntimeException("默认字体加载失败", ex);
        }
    }

    private void copyFile(File source, File output) throws IOException {
        try (var in = new java.io.FileInputStream(source); var out = new java.io.FileOutputStream(output)) {
            in.transferTo(out);
        }
    }
}
