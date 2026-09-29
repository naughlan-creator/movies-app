package dev.naughlan.movies.user;

import dev.naughlan.movies.common.InvalidRequestException;
import dev.naughlan.movies.common.ResourceConflictException;
import dev.naughlan.movies.common.ResourceNotFoundException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
public class UserService {

    // bcrypt ignores everything after 72 bytes, and Spring Security rejects longer input
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String username, String rawPassword) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new InvalidRequestException("password", "Password is too long");
        }

        User user = new User(
                null,
                username.toLowerCase(Locale.ROOT),
                passwordEncoder.encode(rawPassword),
                Set.of(Role.USER),
                Instant.now());

        try {
            return userRepository.insert(user);
        } catch (DuplicateKeyException e) {
            throw new ResourceConflictException("Username taken", "That username is already taken");
        }
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found", "No user named " + username));
    }
}