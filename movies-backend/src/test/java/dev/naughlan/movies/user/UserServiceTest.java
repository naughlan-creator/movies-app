package dev.naughlan.movies.user;

import dev.naughlan.movies.common.InvalidRequestException;
import dev.naughlan.movies.common.ResourceConflictException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private static final String PASSWORD = "correct horse battery staple";

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final UserService service = new UserService(repository, encoder);

    @Test
    void storesAHashNeverThePasswordAndLowercasesTheUsername() {
        when(repository.insert(any(User.class))).thenAnswer(call -> call.getArgument(0));

        service.register("Movie_Fan", PASSWORD);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).insert(saved.capture());
        assertThat(saved.getValue().username()).isEqualTo("movie_fan");
        assertThat(saved.getValue().passwordHash())
                .startsWith("{bcrypt}")
                .doesNotContain(PASSWORD);
        assertThat(encoder.matches(PASSWORD, saved.getValue().passwordHash())).isTrue();
        assertThat(saved.getValue().roles()).containsExactly(Role.USER);
    }

    @Test
    void samePasswordHashesDifferentlyEachTime() {
        // The random salt makes every hash unique, so a leak doesn't reveal shared passwords
        assertThat(encoder.encode(PASSWORD)).isNotEqualTo(encoder.encode(PASSWORD));
    }

    @Test
    void turnsDuplicateKeyIntoConflict() {
        when(repository.insert(any(User.class))).thenThrow(new DuplicateKeyException("E11000"));

        assertThatThrownBy(() -> service.register("taken", PASSWORD))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void rejectsPasswordsOverBcryptsByteLimitBeforeHashing() {
        String emojiPassword = "😀".repeat(20); // 40 chars in Java, but 80 bytes in UTF-8

        assertThatThrownBy(() -> service.register("emoji_fan", emojiPassword))
                .isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).insert(any(User.class));
    }
}