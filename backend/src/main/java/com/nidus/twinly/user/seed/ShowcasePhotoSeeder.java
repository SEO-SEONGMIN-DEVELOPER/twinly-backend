package com.nidus.twinly.user.seed;

import com.nidus.twinly.common.aws.s3.S3Service;
import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.common.photo.ProfileThumbnailService;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@Profile({"prod", "stage", "local"})
@RequiredArgsConstructor
public class ShowcasePhotoSeeder {

    static final String RESOURCE_PATTERN = "seed/showcase-profiles/%02d.webp";
    private static final String CONTENT_TYPE = "image/webp";
    private static final int KEY_HASH_LENGTH = 12;

    private final PhotoRepository photoRepository;
    private final S3Service s3Service;
    private final ProfileThumbnailService profileThumbnailService;

    public void seed(List<User> showcaseUsers) {
        Map<Long, Photo> photoByUserId = photoRepository.findAllByUserIdInAndType(
                        showcaseUsers.stream().map(User::getId).toList(), PhotoType.PROFILE).stream()
                .collect(Collectors.toMap(Photo::getUserId, Function.identity()));

        int seededCount = 0;
        for (int index = 0; index < showcaseUsers.size(); index++) {
            Long userId = showcaseUsers.get(index).getId();
            try {
                if (seed(userId, index, photoByUserId.get(userId))) {
                    seededCount++;
                }
            } catch (IOException | RuntimeException e) {
                WarnLog.log(log, "쇼케이스 프로필 사진을 채우지 못해 건너뜁니다.", e, field("userId", userId));
            }
        }

        InfoLog.log(log, "쇼케이스 프로필 사진을 채웠습니다.", field("seededCount", seededCount));
    }

    private boolean seed(Long userId, int index, Photo existing) throws IOException {
        byte[] image = readResource(index);
        String key = "profile/%d/seed-%s".formatted(userId, hash(image));

        if (existing != null && key.equals(existing.getKey()) && existing.getThumbnailKey() != null) {
            return false;
        }

        PhotoPosInfo position = centerSquare(image);
        s3Service.upload(key, image, CONTENT_TYPE);
        String thumbnailKey = profileThumbnailService.generate(key, position, image.length);

        Photo photo = existing;
        if (photo == null) {
            photo = Photo.create(userId, PhotoType.PROFILE, key,
                    position.startPos().x(), position.startPos().y(), position.width(), position.height(), Instant.now());
        } else {
            photo.changePhoto(key, position.startPos().x(), position.startPos().y(), position.width(), position.height());
        }
        photo.changeThumbnailKey(thumbnailKey);
        photoRepository.save(photo);

        return true;
    }

    private static byte[] readResource(int index) throws IOException {
        try (InputStream in = new ClassPathResource(RESOURCE_PATTERN.formatted(index + 1)).getInputStream()) {
            return in.readAllBytes();
        }
    }

    static PhotoPosInfo centerSquare(byte[] image) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("지원하지 않는 이미지 형식입니다.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int size = Math.min(width, height);

                return new PhotoPosInfo(new PhotoPosInfo.StartPos((width - size) / 2, (height - size) / 2), size, size);
            } finally {
                reader.dispose();
            }
        }
    }

    private static String hash(byte[] image) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image)).substring(0, KEY_HASH_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
        }
    }
}
