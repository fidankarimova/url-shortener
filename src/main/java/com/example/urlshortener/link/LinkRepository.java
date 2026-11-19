package com.example.urlshortener.link;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);
    Page<Link> findByUserId(Long userId, Pageable pageable);

    Optional<Link> findByShortCodeAndUserId(String shortCode, Long userId);
}
