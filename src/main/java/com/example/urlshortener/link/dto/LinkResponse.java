package com.example.urlshortener.link.dto;

import java.time.Instant;

public record LinkResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant createdAt
) {}