package com.example.urlshortener.link;

import com.example.urlshortener.link.dto.CreateLinkRequest;
import com.example.urlshortener.link.dto.LinkResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class LinkController {

    private final LinkService service;

    @PostMapping("/api/links")
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        Link link = service.create(request.url());

        String shortUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/{code}")
                .buildAndExpand(link.getShortCode())
                .toUriString();

        LinkResponse body = new LinkResponse(
                link.getShortCode(), shortUrl, link.getOriginalUrl(), link.getCreatedAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String target = service.resolve(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .build();
    }
}
