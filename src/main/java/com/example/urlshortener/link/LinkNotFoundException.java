package com.example.urlshortener.link;

public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException(String shortCode) {
        super("Link not found: " + shortCode);
    }
}
