package eightjbbm.keepgo.interestextraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@DisplayName("YouTube 동기화 대기 실행기 생명주기")
class YoutubeSyncConfigTest {

    @Test
    @DisplayName("컨텍스트 종료는 대기를 중단하고 다른 컨텍스트의 실행기에 영향을 주지 않는다")
    void interruptsWaitersOnlyInClosedContext() throws InterruptedException {
        try (var first = new AnnotationConfigApplicationContext(YoutubeSyncConfig.class);
                var second = new AnnotationConfigApplicationContext(YoutubeSyncConfig.class)) {
            ExecutorService firstWaiters = first.getBean("youtubeSyncWaiters", ExecutorService.class);
            ExecutorService secondWaiters = second.getBean("youtubeSyncWaiters", ExecutorService.class);
            var started = new CountDownLatch(1);
            var interrupted = new CountDownLatch(1);
            firstWaiters.submit(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    interrupted.countDown();
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            first.close();

            assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(firstWaiters.isShutdown()).isTrue();
            assertThat(secondWaiters.isShutdown()).isFalse();
        }
    }
}
