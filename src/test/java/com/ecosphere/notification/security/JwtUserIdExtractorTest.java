package com.ecosphere.notification.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtUserIdExtractorTest {

    private final JwtUserIdExtractor extractor = new JwtUserIdExtractor();

    @Test
    void sub_claim을_사용자_ID로_변환한다() {
        Jwt jwt = jwtWithSubject("42");

        assertThat(extractor.extract(jwt)).isEqualTo(42L);
    }

    @Test
    void sub_claim이_없으면_실패한다() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("iss", "auth-service"));

        assertThatThrownBy(() -> extractor.extract(jwt))
                .isInstanceOf(JwtUserIdException.class)
                .hasMessage("JWT sub claim이 없습니다.");
    }

    @Test
    void sub_claim이_숫자가_아니면_실패한다() {
        Jwt jwt = jwtWithSubject("user-42");

        assertThatThrownBy(() -> extractor.extract(jwt))
                .isInstanceOf(JwtUserIdException.class)
                .hasMessage("JWT sub claim이 숫자 사용자 ID가 아닙니다.");
    }

    @Test
    void sub_claim이_0이면_실패한다() {
        Jwt jwt = jwtWithSubject("0");

        assertThatThrownBy(() -> extractor.extract(jwt))
                .isInstanceOf(JwtUserIdException.class)
                .hasMessage("JWT sub claim은 양수 사용자 ID여야 합니다.");
    }

    @Test
    void 인증된_객체의_Jwt에서_사용자_ID를_읽는다() {
        Jwt jwt = jwtWithSubject("42");
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(jwt, "token", List.of());

        assertThat(extractor.extract(authentication)).isEqualTo(42L);
    }

    @Test
    void 인증되지_않은_객체는_거부한다() {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        jwtWithSubject("42"), "token");

        assertThatThrownBy(() -> extractor.extract(authentication))
                .isInstanceOf(JwtUserIdException.class)
                .hasMessage("인증된 사용자가 아닙니다.");
    }

    @Test
    void Jwt가_아닌_principal은_거부한다() {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        "user-42", "token", List.of());

        assertThatThrownBy(() -> extractor.extract(authentication))
                .isInstanceOf(JwtUserIdException.class)
                .hasMessage("JWT 인증 정보를 찾을 수 없습니다.");
    }

    private Jwt jwtWithSubject(String subject) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("sub", subject));
    }
}
