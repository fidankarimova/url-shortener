package com.example.urlshortener.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}