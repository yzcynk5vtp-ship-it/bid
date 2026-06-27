package com.xiyu.bid.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.annotation.LogOperation;
import com.xiyu.bid.annotation.Sensitive;
import lombok.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(OperationLogAspectTest.Config.class)
class OperationLogAspectTest {

    @Autowired
    private SampleService sampleService;

    private ListAppender<ILoggingEvent> appender;
    private Logger aspectLogger;

    @BeforeEach
    void setUp() {
        aspectLogger = (Logger) LoggerFactory.getLogger(OperationLogAspect.class);
        aspectLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        aspectLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        aspectLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void logsMethodEntryAndExitWithMaskedArgs() {
        RequestDto req = new RequestDto();
        req.setUsername("alice");
        req.setPassword("secret");

        sampleService.greet(req);

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anyMatch(m -> m.contains("op_start") && m.contains("class=SampleService") && m.contains("method=greet") && m.contains("\"password\":\"***\""));
        assertThat(messages).anyMatch(m -> m.contains("op_end") && m.contains("elapsed=") && m.contains("result=\"hello alice\""));
    }

    @Test
    void logsExceptionOnFailure() {
        assertThatThrownBy(() -> sampleService.boom())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anyMatch(m -> m.contains("op_error") && m.contains("exception=RuntimeException") && m.contains("message=boom"));
    }

    @Test
    void respectsLogLevelAnnotation() {
        sampleService.debugOnly();

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anyMatch(m -> m.contains("op_start") && m.contains("method=debugOnly"));
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class Config {

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public OperationLogAspect operationLogAspect(ObjectMapper objectMapper) {
            return new OperationLogAspect(objectMapper);
        }

        @Bean
        public SampleService sampleService() {
            return new SampleService();
        }
    }

    static class SampleService {

        @LogOperation
        public String greet(RequestDto req) {
            return "hello " + req.getUsername();
        }

        @LogOperation
        public String boom() {
            throw new RuntimeException("boom");
        }

        @LogOperation(level = "DEBUG")
        public String debugOnly() {
            return "debug";
        }
    }

    @Data
    static class RequestDto {
        private String username;
        @Sensitive
        private String password;
    }
}
