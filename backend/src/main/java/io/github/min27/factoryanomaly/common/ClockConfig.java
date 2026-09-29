package io.github.min27.factoryanomaly.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** 시간은 UTC로 저장한다 (D-008). 테스트에서 고정된 Clock으로 바꿔 끼울 수 있다. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
