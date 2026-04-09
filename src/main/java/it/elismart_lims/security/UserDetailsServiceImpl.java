package it.elismart_lims.security;

import it.elismart_lims.model.User;
import it.elismart_lims.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads {@link UserDetails} from the {@code app_user} table for Spring Security's
 * authentication mechanism.
 *
 * <p>The granted authority is derived from the user's {@link it.elismart_lims.model.UserRole},
 * prefixed with {@code ROLE_} as required by Spring Security's
 * {@code hasRole()} expression.</p>
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Finds a user by username and converts them to a Spring Security {@link UserDetails}.
     *
     * @param username the login name to search for
     * @return the populated {@link UserDetails}
     * @throws UsernameNotFoundException if no user with the given username exists
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + username));

        var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(authority))
                .disabled(!user.isEnabled())
                .build();
    }
}
