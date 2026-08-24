package com.nidus.twinly.people.writer;

import com.nidus.twinly.people.domain.TwinViewKind;
import com.nidus.twinly.people.entity.TwinView;
import com.nidus.twinly.people.repository.TwinViewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TwinViewWriterUnitTest {

    private static final Long ME = 1L;

    @Mock
    TwinViewRepository twinViewRepository;

    @InjectMocks
    TwinViewWriter twinViewWriter;

    @Test
    @DisplayName("프로필 열람은 대상·조회자와 PROFILE 종류를 담아 저장한다")
    void write_saves_profile_view() {
        // when: 남의 프로필 열람 기록
        twinViewWriter.write(20L, ME, TwinViewKind.PROFILE);

        // then: 대상은 상대, 조회자는 나, 종류는 PROFILE
        assertThat(savedView())
                .extracting(TwinView::getTargetUserId, TwinView::getViewerUserId, TwinView::getKind)
                .containsExactly(20L, ME, TwinViewKind.PROFILE);
    }

    @Test
    @DisplayName("이벤트 열람은 EVENT 종류로 저장된다")
    void write_saves_event_view() {
        // when: 남의 이벤트 목록 열람 기록
        twinViewWriter.write(20L, ME, TwinViewKind.EVENT);

        // then: 종류만 EVENT 로 구분된다
        assertThat(savedView().getKind()).isEqualTo(TwinViewKind.EVENT);
    }

    @Test
    @DisplayName("본인 열람은 종류와 무관하게 기록하지 않는다")
    void write_skips_self_view() {
        // when: 본인 프로필·이벤트 열람 기록
        twinViewWriter.write(ME, ME, TwinViewKind.PROFILE);
        twinViewWriter.write(ME, ME, TwinViewKind.EVENT);

        // then: 저장 없음
        then(twinViewRepository).should(never()).save(any());
    }

    private TwinView savedView() {
        ArgumentCaptor<TwinView> captor = ArgumentCaptor.forClass(TwinView.class);
        then(twinViewRepository).should().save(captor.capture());
        return captor.getValue();
    }
}
