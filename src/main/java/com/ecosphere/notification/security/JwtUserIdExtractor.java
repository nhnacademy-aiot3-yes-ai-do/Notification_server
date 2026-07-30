package com.ecosphere.notification.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Spring Security가 서명과 만료 시간을 검증한 JWT에서 사용자 ID를 추출한다.
 * 이 컴포넌트 자체는 토큰을 검증하거나 디코딩하지 않는다.
 * Auth 서비스의 계약상 사용자 ID는 표준 sub claim에 문자열로 들어온다.
 */
@Component
public class JwtUserIdExtractor {

    private static final String SUBJECT_CLAIM = "sub";

    public Long extract(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new JwtUserIdException("인증된 사용자가 아닙니다.");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            throw new JwtUserIdException("JWT 인증 정보를 찾을 수 없습니다.");
        }

        return extract(jwt);
    }

    public Long extract(Jwt jwt) {
        if (jwt == null) {
            throw new JwtUserIdException("JWT가 없습니다.");
        }

        String subject = jwt.getClaimAsString(SUBJECT_CLAIM);
        if (subject == null || subject.isBlank()) {
            throw new JwtUserIdException("JWT sub claim이 없습니다.");
        }

        try {
            long userId = Long.parseLong(subject);
            if (userId < 1) {
                throw new JwtUserIdException("JWT sub claim은 양수 사용자 ID여야 합니다.");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new JwtUserIdException("JWT sub claim이 숫자 사용자 ID가 아닙니다.", exception);
        }
    }
}
