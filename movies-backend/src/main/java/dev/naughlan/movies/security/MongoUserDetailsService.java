package dev.naughlan.movies.security;

import dev.naughlan.movies.user.Role;
import dev.naughlan.movies.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
class MongoUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    MongoUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsername(username.toLowerCase(Locale.ROOT))
                .map(user -> org.springframework.security.core.userdetails.User
                        .withUsername(user.username())
                        .password(user.passwordHash())
                        .roles(user.roles().stream().map(Role::name).toArray(String[]::new))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}