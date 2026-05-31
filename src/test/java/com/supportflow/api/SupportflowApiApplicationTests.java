package com.supportflow.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.supportflow.api.user.UserRepository;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "app.security.jwt.secret=test-secret-for-context-load-1234567890",
        "app.security.jwt.expiration=3600000"
})
class SupportflowApiApplicationTests {

    @MockBean
    UserRepository userRepository;

    @Test
    void contextLoads() {
    }
}
