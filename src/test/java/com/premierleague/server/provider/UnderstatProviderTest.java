package com.premierleague.server.provider;

import com.premierleague.server.config.CacheConfig;
import com.premierleague.server.model.PlayerStat;
import com.premierleague.server.util.HttpClientUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        CacheConfig.class,
        UnderstatProvider.class,
        UnderstatProviderTest.TestConfig.class
})
class UnderstatProviderTest {

    @Configuration
    static class TestConfig {
        @Bean
        HttpClientUtil httpClientUtil() {
            return Mockito.mock(HttpClientUtil.class);
        }
    }

    @Autowired
    private UnderstatProvider understatProvider;

    @Autowired
    private HttpClientUtil httpClientUtil;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("fdUnderstatScorers").clear();
        ReflectionTestUtils.setField(understatProvider, "season", "2025");
        Mockito.reset(httpClientUtil);
    }

    @Test
    void concurrentRequestsShareSingleUnderlyingFetch() throws Exception {
        when(httpClientUtil.postForm(anyString(), anyString(), anyMap()))
                .thenAnswer(invocation -> {
                    Thread.sleep(300);
                    return """
                            {"success":true,"players":[
                              {"id":"8260","player_name":"Erling Haaland","games":"30","goals":"22","assists":"7","npg":"19","position":"F S","team_title":"Manchester City"}
                            ]}
                            """;
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<List<PlayerStat>> task = understatProvider::fetchScorers;
            Future<List<PlayerStat>> first = executor.submit(task);
            Future<List<PlayerStat>> second = executor.submit(task);

            List<PlayerStat> firstResult = get(first);
            List<PlayerStat> secondResult = get(second);

            assertEquals(1, firstResult.size());
            assertEquals(1, secondResult.size());
            assertEquals("Erling Haaland", firstResult.get(0).playerName());
            assertFalse(firstResult.isEmpty());
            verify(httpClientUtil, times(1)).postForm(anyString(), anyString(), anyMap());
        } finally {
            executor.shutdownNow();
        }
    }

    private List<PlayerStat> get(Future<List<PlayerStat>> future) throws InterruptedException, ExecutionException {
        return future.get();
    }
}
