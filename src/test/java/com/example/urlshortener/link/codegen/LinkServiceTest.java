package com.example.urlshortener.link.codegen;

import com.example.urlshortener.link.Link;
import com.example.urlshortener.link.LinkNotFoundException;
import com.example.urlshortener.link.LinkRepository;
import com.example.urlshortener.link.LinkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock
    private LinkRepository repository;

    @Mock
    private ShortCodeGenerator generator;

    @InjectMocks
    private LinkService service;

    @Test
    void create_savesLinkWithGeneratedCode() {
        when(generator.generate()).thenReturn("abc1234");
        when(repository.save(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));

        Link result = service.create("https://github.com");

        assertThat(result.getShortCode()).isEqualTo("abc1234");
        assertThat(result.getOriginalUrl()).isEqualTo("https://github.com");
    }

    @Test
    void create_retriesWhenCodeCollides() {
        when(generator.generate()).thenReturn("dup0001", "uniq002");
        when(repository.save(any(Link.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"))
                .thenAnswer(inv -> inv.getArgument(0));

        Link result = service.create("https://github.com");

        assertThat(result.getShortCode()).isEqualTo("uniq002");
        verify(repository, times(2)).save(any(Link.class));
    }

    @Test
    void create_givesUpAfterMaxAttempts() {
        when(generator.generate()).thenReturn("dup0001");
        when(repository.save(any(Link.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create("https://github.com"))
                .isInstanceOf(IllegalStateException.class);

        verify(repository, times(5)).save(any(Link.class));
    }

    @Test
    void resolve_returnsOriginalUrl() {
        Link link = new Link();
        link.setShortCode("abc1234");
        link.setOriginalUrl("https://github.com");
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(link));

        assertThat(service.resolve("abc1234")).isEqualTo("https://github.com");
    }

    @Test
    void resolve_throwsWhenNotFound() {
        when(repository.findByShortCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("nope"))
                .isInstanceOf(LinkNotFoundException.class);
    }
}