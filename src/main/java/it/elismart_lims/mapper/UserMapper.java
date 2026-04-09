package it.elismart_lims.mapper;

import it.elismart_lims.dto.UserResponse;
import it.elismart_lims.model.User;

import java.util.List;

/**
 * Static mapper between {@link User} entities and their response DTOs.
 *
 * <p>The {@code password} field is never copied to any DTO.</p>
 */
public final class UserMapper {

    private UserMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a {@link User} entity to a {@link UserResponse} DTO.
     *
     * @param user the user entity
     * @return the response DTO (password excluded)
     */
    public static UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * Converts a list of {@link User} entities to a list of {@link UserResponse} DTOs.
     *
     * @param users the user entities
     * @return the list of response DTOs
     */
    public static List<UserResponse> toResponseList(List<User> users) {
        return users.stream().map(UserMapper::toResponse).toList();
    }
}
