package com.example.urlshortener.link.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateLinkRequest(
        @NotBlank
        @Size(max = 2048)
        @URL(regexp = "^(http|https)://.*")
        String url
) {}
