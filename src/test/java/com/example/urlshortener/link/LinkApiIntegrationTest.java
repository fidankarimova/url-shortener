package com.example.urlshortener.link;

import com.example.urlshortener.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class LinkApiIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("app.rate-limit.requests-per-minute", () -> 10_000);
    }

    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy springSecurityFilterChain;
    @Autowired private LinkRepository linkRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();

        var keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        linkRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndLogin(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"supersecret123\"}";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.accessToken");
    }

    private String createLink(String bearer, String url) throws Exception {
        String response = mockMvc.perform(post("/api/links")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.shortCode");
    }

    @Test
    void createsLinkAndRedirectsWithoutLogin() throws Exception {
        String bearer = registerAndLogin("a@test.com");
        String code = createLink(bearer, "https://github.com");

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://github.com"));
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsGarbageToken() throws Exception {
        mockMvc.perform(get("/api/links").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidUrl() throws Exception {
        String bearer = registerAndLogin("a@test.com");
        mockMvc.perform(post("/api/links")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404ForUnknownCode() throws Exception {
        mockMvc.perform(get("/doesnotexist")).andExpect(status().isNotFound());
    }

    @Test
    void userSeesOnlyOwnLinks() throws Exception {
        String alice = registerAndLogin("alice@test.com");
        String bob = registerAndLogin("bob@test.com");
        createLink(alice, "https://alice.com");

        mockMvc.perform(get("/api/links").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/links").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void cannotDeleteSomeoneElsesLink() throws Exception {
        String alice = registerAndLogin("alice@test.com");
        String bob = registerAndLogin("bob@test.com");
        String code = createLink(alice, "https://alice.com");

        mockMvc.perform(delete("/api/links/" + code).header("Authorization", bob))
                .andExpect(status().isNotFound());

        // the owner can delete it
        mockMvc.perform(get("/" + code)).andExpect(status().isFound());   // populates the cache
        mockMvc.perform(delete("/api/links/" + code).header("Authorization", alice))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/" + code)).andExpect(status().isNotFound()); // cache entry was evicted too
    }

    @Test
    void redirectIsCachedInRedis() throws Exception {
        String bearer = registerAndLogin("a@test.com");
        String code = createLink(bearer, "https://github.com");

        assertThat(redisTemplate.opsForValue().get("link:" + code)).isNull();

        mockMvc.perform(get("/" + code)).andExpect(status().isFound());

        assertThat(redisTemplate.opsForValue().get("link:" + code)).isEqualTo("https://github.com");
    }

    @Test
    void databaseRejectsDuplicateShortCode() {
        Link first = new Link();
        first.setShortCode("same123");
        first.setOriginalUrl("https://a.com");
        linkRepository.save(first);

        Link second = new Link();
        second.setShortCode("same123");
        second.setOriginalUrl("https://b.com");

        assertThatThrownBy(() -> linkRepository.save(second))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(linkRepository.count()).isEqualTo(1);
    }
}