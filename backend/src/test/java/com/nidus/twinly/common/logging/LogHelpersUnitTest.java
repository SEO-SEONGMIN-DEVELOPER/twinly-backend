package com.nidus.twinly.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import static com.nidus.twinly.common.logging.LogField.field;
import static org.assertj.core.api.Assertions.assertThat;

class LogHelpersUnitTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LogHelpersUnitTest.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("InfoLog: 문장 뒤에 key=value 를 붙여 사람이 읽게 하고, 같은 값을 구조화 필드로도 남긴다")
    void info_renders_message_and_fields() {
        // when
        InfoLog.log(logger, "시드 유저를 채웠습니다.", field("userCount", 20), field("elementCount", 400));

        // then: 메시지에는 문장 + 필드, 이벤트에는 key-value 쌍
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo("시드 유저를 채웠습니다. userCount=20, elementCount=400");
        assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key + "=" + pair.value)
                .containsExactly("userCount=20", "elementCount=400");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("InfoLog: 필드가 없으면 문장만 남긴다")
    void info_without_fields_renders_message_only() {
        // when
        InfoLog.log(logger, "탈퇴 유저 파기를 완료했습니다.");

        // then
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getFormattedMessage()).isEqualTo("탈퇴 유저 파기를 완료했습니다.");
        assertThat(event.getKeyValuePairs()).isNull();
    }

    @Test
    @DisplayName("WarnLog: 원인 예외를 넘기면 스택 트레이스가 함께 남는다")
    void warn_with_cause_attaches_throwable() {
        // given
        IllegalStateException cause = new IllegalStateException("SOLAPI 장애");

        // when
        WarnLog.log(logger, "썸네일 생성에 실패해 아바타 없이 진행합니다.", cause, field("key", "photos/1.jpg"));

        // then
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo("썸네일 생성에 실패해 아바타 없이 진행합니다. key=photos/1.jpg");
        assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key).containsExactly("key");
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("SOLAPI 장애");
    }

    @Test
    @DisplayName("WarnLog: 원인 예외 없이 필드만으로도 남길 수 있다")
    void warn_without_cause() {
        // when
        WarnLog.log(logger, "푸시 작업 큐가 가득 차 발송을 건너뜁니다.", field("queued", 1000));

        // then
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo("푸시 작업 큐가 가득 차 발송을 건너뜁니다. queued=1000");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("null 값 필드는 null 로 그대로 남긴다 (값이 없었다는 사실 자체가 정보다)")
    void null_field_value_is_rendered_as_null() {
        // when
        WarnLog.log(logger, "NICE 요청이 실패했습니다.", field("resultCode", null));

        // then
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getFormattedMessage()).isEqualTo("NICE 요청이 실패했습니다. resultCode=null");
        KeyValuePair pair = event.getKeyValuePairs().getFirst();
        assertThat(pair.key).isEqualTo("resultCode");
        assertThat(pair.value).isNull();
    }
}
