package com.zhishuiyun.water.security;

import com.zhishuiyun.water.entity.DomainEnums;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    @Test
    void randomKeyIsPersistedAndExistingSessionsSurviveRestart() throws Exception {
        Path path = keyPath();
        JwtService first = new JwtService("", 480, path.toString());
        String token = first.generate("test-admin", DomainEnums.AccountRole.ADMIN, "测试账户");
        JwtService restarted = new JwtService("", 480, path.toString());
        assertThat(Files.size(path)).isGreaterThanOrEqualTo(64);
        assertThat(restarted.parse(token).getSubject()).isEqualTo("test-admin");
    }

    @Test
    void independentInstallationsDoNotShareSigningKey() throws Exception {
        JwtService first = new JwtService("", 480, keyPath().toString());
        JwtService second = new JwtService("", 480, keyPath().toString());
        String token = first.generate("test-admin", DomainEnums.AccountRole.ADMIN, "测试账户");
        assertThatThrownBy(() -> second.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void insecureConfiguredKeyIsRejected() {
        assertThatThrownBy(() -> new JwtService("weak", 480, "not-used"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Path keyPath() throws Exception {
        Path parent = Path.of("target", "jwt-tests").toAbsolutePath();
        Files.createDirectories(parent);
        return parent.resolve(UUID.randomUUID() + ".key");
    }
}
