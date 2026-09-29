package io.github.min27.factoryanomaly.reading;

import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/readings")
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingService readingService;

    @PostMapping
    public ResponseEntity<ReadingResponse> ingest(@Valid @RequestBody ReadingRequest request) {
        ReadingResponse response = readingService.ingest(request);
        return ResponseEntity.created(URI.create("/api/readings/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ReadingResponse get(@PathVariable long id) {
        return readingService.get(id);
    }
}
