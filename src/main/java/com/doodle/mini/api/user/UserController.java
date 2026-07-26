package com.doodle.mini.api.user;

import com.doodle.mini.service.user.UserService;
import com.doodle.mini.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> create(
        @Valid @RequestBody CreateUserRequest request) {

        UserResponse response = userService.create(request);
        return ResponseEntity
            .created(URI.create("/api/v1/users/" + response.id()))
            .body(response);
    }

    @GetMapping("/{userId}")
    public UserResponse findById(@PathVariable UUID userId) {
        return userService.findById(userId);
    }

    @GetMapping("/email")
    public UserResponse findByEmail(@RequestParam @Email String email) {
        return userService.findByEmail(email);
    }

    @GetMapping
    public PageResponse<UserResponse> findAll(@PageableDefault(sort = "name") Pageable pageable) {
        return userService.findAll(pageable);
    }

    @PutMapping("/{userId}") // we can also implement a patch to update just a specific field
    public UserResponse update(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(userId, request);
    }
}
