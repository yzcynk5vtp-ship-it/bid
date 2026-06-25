package com.xiyu.bid.repository;

import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositorySearchTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void searchActiveUsers_MatchesEmployeeNumber() {
        User user = User.builder()
            .username("zhangsan")
            .email("zhangsan@example.com")
            .password("p")
            .fullName("张三")
            .employeeNumber("E009")
            .role(User.Role.MANAGER)
            .enabled(true)
            .build();
        entityManager.persist(user);
        entityManager.flush();

        List<User> results = userRepository.searchActiveUsers("E009", 10);

        assertThat(results)
            .extracting(User::getFullName)
            .containsExactly("张三");
    }

    @Test
    void searchActiveUsers_ExcludesDisabledUsers() {
        User user = User.builder()
            .username("lisi")
            .email("lisi@example.com")
            .password("p")
            .fullName("李四")
            .employeeNumber("E010")
            .role(User.Role.MANAGER)
            .enabled(false)
            .build();
        entityManager.persist(user);
        entityManager.flush();

        List<User> results = userRepository.searchActiveUsers("E010", 10);

        assertThat(results).isEmpty();
    }
}
