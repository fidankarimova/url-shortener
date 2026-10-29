package com.example.urlshortener.link;

import com.example.urlshortener.link.codegen.ShortCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkService {

    private static final int MAX_ATTEMPTS = 5;

    private final LinkRepository repository;
    private final ShortCodeGenerator generator;

    public Link create(String originalUrl) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Link link = new Link();
            link.setShortCode(generator.generate());
            link.setOriginalUrl(originalUrl);
            try {
                return repository.save(link);
            } catch (DataIntegrityViolationException e) {
                // Eyni kod artıq var (unique index pozuldu), yenisini yoxla
            }
        }
        throw new IllegalStateException("Could not generate a unique short code");
    }

    @Transactional(readOnly = true)
    public String resolve(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(Link::getOriginalUrl)
                .orElseThrow(() -> new LinkNotFoundException(shortCode));
    }
}
