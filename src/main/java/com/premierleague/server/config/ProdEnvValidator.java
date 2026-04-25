package com.premierleague.server.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 生产环境必须设置的环境变量校验。
 *
 * <h2>为什么用 {@link EnvironmentPostProcessor} 而不是 {@code @PostConstruct}？</h2>
 * {@link EnvironmentPostProcessor} 在 Spring context 构建之前就执行，因此可以在 HikariCP
 * 尝试连接 DB 之前拦截错误。如果用普通 {@code @Component} + {@code @PostConstruct}，
 * 缺失的 DB 密码会先让 Hikari 抛出一句含糊的 "Access denied using password: NO"，
 * 运维要翻半天才定位到是环境变量没设。在这里拦下能打印清晰的 "DB_PASSWORD not set"。
 *
 * <h2>注册</h2>
 * 通过 {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}
 * 注册——@Component 不会被 Spring Boot 在这个阶段发现。
 *
 * <h2>校验项</h2>
 * <ul>
 *   <li>{@code spring.datasource.password} — DB_PASSWORD 必须非空</li>
 *   <li>{@code app.admin.api-token} — ADMIN_API_TOKEN 必须非空（admin 接口才有鉴权）</li>
 * </ul>
 * 仅在 {@code prod} profile 激活时检查，开发环境照常跑。
 */
public class ProdEnvValidator implements EnvironmentPostProcessor, Ordered {

    private static final String PROD_PROFILE = "prod";

    /**
     * 属性名 → 推荐的环境变量名（仅用于错误信息展示，方便运维直接知道该导哪个 env）。
     */
    private static final String[][] REQUIRED_PROPS = {
            {"spring.datasource.password", "DB_PASSWORD"},
            {"app.admin.api-token", "ADMIN_API_TOKEN"},
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isProdActive(environment)) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String[] pair : REQUIRED_PROPS) {
            String propKey = pair[0];
            String envHint = pair[1];
            String value = environment.getProperty(propKey);
            if (value == null || value.isBlank()) {
                missing.add(envHint + " (property " + propKey + ")");
            }
        }
        if (!missing.isEmpty()) {
            // 直接抛异常 → Spring Boot 启动失败，stderr 会打印出这条信息
            throw new IllegalStateException(
                    "prod profile active but required environment variables are missing: "
                            + String.join(", ", missing)
                            + ". Set them before starting the container.");
        }
    }

    private static boolean isProdActive(ConfigurableEnvironment env) {
        String[] active = env.getActiveProfiles();
        if (active.length > 0) {
            return Arrays.asList(active).contains(PROD_PROFILE);
        }
        // 兼容通过系统属性 / env var 直接传递 SPRING_PROFILES_ACTIVE 的场景
        String fallback = env.getProperty("spring.profiles.active", "");
        return Arrays.asList(fallback.split(",")).contains(PROD_PROFILE);
    }

    @Override
    public int getOrder() {
        // 在 Spring Boot 自己加载 application.yml 的 ConfigDataEnvironmentPostProcessor 之后执行，
        // 保证我们能读到 application-prod.yml 里的任何覆盖值
        return Ordered.LOWEST_PRECEDENCE;
    }
}
