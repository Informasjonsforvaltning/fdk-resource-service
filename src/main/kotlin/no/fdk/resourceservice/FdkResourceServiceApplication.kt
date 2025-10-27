package no.fdk.resourceservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka

@SpringBootApplication
@EnableKafka
class FdkResourceServiceApplication

fun main(args: Array<String>) {
    runApplication<no.fdk.resourceservice.FdkResourceServiceApplication>(*args)
}





