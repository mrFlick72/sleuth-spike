package it.valeriovaudi.sleuth.sleuthspike;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class SleuthSpikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SleuthSpikeApplication.class, args);
    }
}

@Slf4j
@RestController
class HelloEndPoint {

    @GetMapping("/hello/{name}")
    public ResponseEntity hello(@PathVariable String name) {

        log.info("hello " + name);
        return ResponseEntity.ok("hello " + name);
    }
}