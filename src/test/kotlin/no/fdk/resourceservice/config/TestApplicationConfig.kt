package no.fdk.resourceservice.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

@TestConfiguration
@ComponentScan(
    basePackages = ["no.fdk.resourceservice"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["no.fdk.resourceservice.kafka.*"],
        ),
    ],
)
class TestApplicationConfig
