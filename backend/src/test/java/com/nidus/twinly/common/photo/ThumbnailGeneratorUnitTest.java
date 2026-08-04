package com.nidus.twinly.common.photo;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class ThumbnailGeneratorUnitTest {

    // 1x1 WebP. 이 환경에 webp 변환 도구가 없어 최소 파일을 상수로 둔다.
    private static final String ONE_PIXEL_WEBP_BASE64 = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==";

    private final ThumbnailGenerator thumbnailGenerator = new ThumbnailGenerator();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("JPEG 원본을 지정한 크롭 영역만 잘라 128x128 JPEG으로 만든다")
    void generate_jpeg_crops_and_resizes() throws IOException {
        // given: 왼쪽 절반은 빨강, 오른쪽 절반은 파랑인 400x200 JPEG 파일
        Path source = halfAndHalf("jpg", 400, 200, Color.RED, Color.BLUE);

        // when: 오른쪽 절반(파랑)만 크롭
        byte[] thumbnail = thumbnailGenerator.generate(source, position(200, 0, 200, 200));

        // then: 128x128로 나오고, 잘라낸 영역의 색인 파랑만 담긴다
        BufferedImage result = decode(thumbnail);
        assertThat(result.getWidth()).isEqualTo(128);
        assertThat(result.getHeight()).isEqualTo(128);
        assertThatColorIsNear(result.getRGB(64, 64), Color.BLUE);
    }

    @Test
    @DisplayName("크롭 좌표가 다르면 잘라낸 영역의 색도 다르게 나온다")
    void generate_respects_crop_origin() throws IOException {
        // given: 동일한 원본
        Path source = halfAndHalf("jpg", 400, 200, Color.RED, Color.BLUE);

        // when: 왼쪽 절반(빨강)을 크롭
        byte[] thumbnail = thumbnailGenerator.generate(source, position(0, 0, 200, 200));

        // then: 앞 테스트와 같은 원본인데 빨강이 나온다 (좌표가 실제로 반영됨)
        assertThatColorIsNear(decode(thumbnail).getRGB(64, 64), Color.RED);
    }

    @Test
    @DisplayName("알파 채널이 있는 PNG도 색이 깨지지 않고 128x128 JPEG으로 변환된다")
    void generate_png_with_alpha() throws IOException {
        // given: 알파 채널을 가진 PNG (JPEG은 알파를 담지 못해 그대로 쓰면 색이 뒤집힌다)
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 300, 300);
        graphics.dispose();

        Path source = tempDir.resolve("alpha.png");
        ImageIO.write(image, "png", source.toFile());

        // when: 전체 영역을 크롭
        byte[] thumbnail = thumbnailGenerator.generate(source, position(0, 0, 300, 300));

        // then: 파랑이 유지된다
        BufferedImage result = decode(thumbnail);
        assertThat(result.getWidth()).isEqualTo(128);
        assertThatColorIsNear(result.getRGB(64, 64), Color.BLUE);
    }

    @Test
    @DisplayName("WebP 리더가 등록되어 있어 WebP 원본도 읽을 수 있다")
    void generate_reads_webp() throws IOException {
        // given: TwelveMonkeys 플러그인이 클래스패스에 있으면 SPI로 자동 등록된다
        byte[] webp = Base64.getDecoder().decode(ONE_PIXEL_WEBP_BASE64);

        // when: WebP를 읽는다
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(webp));

        // then: 표준 ImageIO만으로는 null이 반환된다 (플러그인이 붙어야 디코딩된다)
        assertThat(decoded).isNotNull();
    }

    @Test
    @DisplayName("4000x3000 원본도 전체를 메모리에 올리지 않고 128x128로 변환된다")
    void generate_large_source() throws IOException {
        // given: 통째로 디코딩하면 약 48MB인 원본. 파일로 두어 힙에 올리지 않는다
        Path source = halfAndHalf("jpg", 4000, 3000, Color.RED, Color.BLUE);

        // when: 3000x3000 정사각 영역을 크롭하며 이 스레드의 할당량을 잰다
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long threadId = Thread.currentThread().threadId();
        long before = threadMXBean.getThreadAllocatedBytes(threadId);

        byte[] thumbnail = thumbnailGenerator.generate(source, position(1000, 0, 3000, 3000));

        long allocatedMb = (threadMXBean.getThreadAllocatedBytes(threadId) - before) / (1024 * 1024);

        // then: 128x128로 완료되고, 전체 디코딩(약 48MB)에 한참 못 미치는 양만 할당된다.
        //       setSourceSubsampling이 빠지거나 파일 대신 byte[]를 읽으면 이 단정이 깨진다.
        BufferedImage result = decode(thumbnail);
        assertThat(result.getWidth()).isEqualTo(128);
        assertThat(result.getHeight()).isEqualTo(128);
        assertThat(allocatedMb).isLessThan(8L);
    }

    @Test
    @DisplayName("가로가 짧고 세로가 긴 크롭도 축별로 솎아내어 메모리 상한을 지킨다")
    void generate_bounds_memory_for_extreme_aspect_crop() throws IOException {
        // given: 세로로 아주 긴 원본
        Path source = halfAndHalf("jpg", 400, 6000, Color.RED, Color.BLUE);

        // when: 가로 200 · 세로 6000인 극단적 비율로 크롭하며 할당량을 잰다
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long threadId = Thread.currentThread().threadId();
        long before = threadMXBean.getThreadAllocatedBytes(threadId);

        byte[] thumbnail = thumbnailGenerator.generate(source, position(0, 0, 200, 6000));

        long allocatedMb = (threadMXBean.getThreadAllocatedBytes(threadId) - before) / (1024 * 1024);

        // then: 가로 배수(1)를 세로에도 쓰면 200x6000을 통째로 다루느라 34MB가 든다.
        //       축을 나눠 세로만 32배로 솎아내면 상한 안에 머문다.
        assertThat(decode(thumbnail).getWidth()).isEqualTo(128);
        assertThat(allocatedMb).isLessThan(4L);
    }

    @Test
    @DisplayName("크롭 영역이 원본을 벗어나면 IOException으로 실패한다")
    void generate_rejects_out_of_bounds_crop() throws IOException {
        // given: ImageIO는 벗어난 영역을 예외 없이 잘라내므로, 그대로 두면
        //        비율이 눌린 썸네일이 조용히 만들어진다
        Path source = halfAndHalf("jpg", 400, 200, Color.RED, Color.BLUE);

        // when & then: 원본(400x200)을 넘어서는 크롭은 거부된다
        assertThatThrownBy(() -> thumbnailGenerator.generate(source, position(300, 0, 200, 200)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("크롭 영역이 원본을 벗어났습니다");
    }

    @Test
    @DisplayName("이미지가 아닌 파일이 들어오면 IOException으로 실패한다")
    void generate_rejects_non_image() throws IOException {
        // given: 이미지가 아닌 파일
        Path source = Files.write(tempDir.resolve("not-an-image.txt"), "not-an-image".getBytes());

        // when & then: 리더를 찾지 못해 실패한다
        assertThatThrownBy(() -> thumbnailGenerator.generate(source, position(0, 0, 100, 100)))
                .isInstanceOf(IOException.class);
    }

    private PhotoPosInfo position(int x, int y, int width, int height) {
        return new PhotoPosInfo(new PhotoPosInfo.StartPos(x, y), width, height);
    }

    private Path halfAndHalf(String format, int width, int height, Color left, Color right) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(left);
        graphics.fillRect(0, 0, width / 2, height);
        graphics.setColor(right);
        graphics.fillRect(width / 2, 0, width - width / 2, height);
        graphics.dispose();

        Path source = tempDir.resolve("source-%dx%d.%s".formatted(width, height, format));
        ImageIO.write(image, format, source.toFile());
        return source;
    }

    private BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private void assertThatColorIsNear(int rgb, Color expected) {
        Color actual = new Color(rgb);
        assertThat(actual.getRed()).isCloseTo(expected.getRed(), offset(30));
        assertThat(actual.getGreen()).isCloseTo(expected.getGreen(), offset(30));
        assertThat(actual.getBlue()).isCloseTo(expected.getBlue(), offset(30));
    }
}
