package eightjbbm.keepgo;

import io.sentry.Sentry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class KeepgoApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void sendTestException() {
		Sentry.captureException(
				new RuntimeException("휴이로부터의 Spring Boot Sentry 테스트")
		);
		Sentry.flush(5_000);
	}
}
