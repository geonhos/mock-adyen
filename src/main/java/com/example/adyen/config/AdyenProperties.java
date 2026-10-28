package com.example.adyen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * {@code application-local.yml} 바인딩 전용. 환경(test/live)별 자격증명을
 * {@link #environments} 맵에 담고, 맵의 키가 곧 환경 이름이다.
 *
 * <p>런타임에 활성 환경을 고르고 값을 읽는 것은 {@link AdyenEnvironment} 의 책임이다.
 * 이 레코드를 직접 주입받아 쓰지 말 것 — 활성 환경을 무시하게 된다.
 */
@ConfigurationProperties(prefix = "adyen")
public record AdyenProperties(
        /** 부팅 시 활성화할 환경 이름. {@link #environments} 의 키여야 한다. */
        String activeEnvironment,
        /** Sessions 요청의 returnUrl. test/live 모두 로컬 콘솔이라 환경 공통이다. */
        String returnUrl,
        Map<String, EnvConfig> environments
) {

    /** 한 환경(test 또는 live)의 자격증명 묶음. */
    public record EnvConfig(
            String apiKey,
            String merchantAccount,
            String baseUrl,
            String clientKey,
            /** Web SDK(Components) CDN 호스트. 예: {@code https://checkoutshopper-test.adyen.com} */
            String sdkBaseUrl
    ) {
        /**
         * 이 환경으로 실제 호출이 가능한지. 미기입 블록(운영 키를 아직 안 받은 상태)을
         * 활성화하려는 시도를 막고, 콘솔 셀렉터에서 선택 불가로 표시하는 데 쓴다.
         */
        public boolean configured() {
            return notBlank(apiKey) && notBlank(merchantAccount) && notBlank(baseUrl);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }
}
