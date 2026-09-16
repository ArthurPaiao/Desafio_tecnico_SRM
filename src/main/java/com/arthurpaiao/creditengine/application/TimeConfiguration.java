package com.arthurpaiao.creditengine.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class TimeConfiguration {
    @Bean
    Clock clock() { return Clock.systemUTC(); }
}
