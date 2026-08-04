package com.nidus.twinly.common.photo;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

@Component
public class ThumbnailGenerator {

    private static final int TARGET_SIZE = 128;
    private static final String OUTPUT_FORMAT = "jpg";

    public byte[] generate(Path source, PhotoPosInfo position) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("지원하지 않는 이미지 형식입니다.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                validateWithinBounds(position, reader.getWidth(0), reader.getHeight(0));

                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(subsampling(position.width()), subsampling(position.height()), 0, 0);
                param.setSourceRegion(new Rectangle(
                        position.startPos().x(), position.startPos().y(),
                        position.width(), position.height()));

                return encode(scale(reader.read(0, param)));
            } finally {
                reader.dispose();
            }
        }
    }

    private void validateWithinBounds(PhotoPosInfo position, int sourceWidth, int sourceHeight) throws IOException {
        int x = position.startPos().x();
        int y = position.startPos().y();
        int width = position.width();
        int height = position.height();

        if (x < 0 || y < 0 || width <= 0 || height <= 0
                || x + width > sourceWidth || y + height > sourceHeight) {
            throw new IOException("크롭 영역이 원본을 벗어났습니다: source=%dx%d, crop=(%d,%d,%d,%d)"
                    .formatted(sourceWidth, sourceHeight, x, y, width, height));
        }
    }

    private int subsampling(int cropWidth) {
        int factor = 1;
        while (cropWidth / (factor * 2) >= TARGET_SIZE) {
            factor *= 2;
        }
        return factor;
    }

    private BufferedImage scale(BufferedImage source) {
        Image scaled = source.getScaledInstance(TARGET_SIZE, TARGET_SIZE, Image.SCALE_SMOOTH);

        BufferedImage result = new BufferedImage(TARGET_SIZE, TARGET_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(scaled, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        return result;
    }

    private byte[] encode(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, OUTPUT_FORMAT, output);
        return output.toByteArray();
    }
}
