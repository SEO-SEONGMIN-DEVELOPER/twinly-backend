package com.nidus.twinly.anon.entity;

import com.nidus.twinly.common.photo.PhotoType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnonSessionPhotoTest {

    @Test
    @DisplayName("업로드 시각과 생성 시각은 같은 이벤트의 시각이므로 정확히 일치한다")
    void uploaded_at_and_created_at_are_same_instant() {
        // when: 익명 세션 사진 생성
        AnonSessionPhoto photo = AnonSessionPhoto.create(1L, PhotoType.PROFILE, "key", 0, 0, 100, 100);

        // then: 한 이벤트에는 하나의 시각을 쓴다
        assertThat(photo.getUploadedAt()).isEqualTo(photo.getCreatedAt());
    }
}
