package com.example.chat.controller;

import com.example.chat.dto.Dtos.LoginRequest;
import com.example.chat.dto.Dtos.UserDto;
import com.example.chat.model.AppUser;
import com.example.chat.repo.UserRepository;
import com.example.chat.service.RedisStateService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final RedisStateService redisState;

    public UserController(UserRepository userRepository, RedisStateService redisState) {
        this.userRepository = userRepository;
        this.redisState = redisState;
    }

    /** POC "login": creates the user if it does not exist. No password. */
    @PostMapping("/login")
    public UserDto login(@RequestBody LoginRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        if (!username.matches("^[A-Za-z0-9_]{2,20}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Username must be 2-20 chars: letters, digits, underscore");
        }
        AppUser user = userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(new AppUser(username)));
        return new UserDto(user.getUsername(), redisState.isOnline(username), user.getLastSeen());
    }

    @GetMapping
    public List<UserDto> all() {
        Set<String> online = redisState.onlineUsers();
        return userRepository.findAll().stream()
                .map(u -> new UserDto(u.getUsername(), online.contains(u.getUsername()), u.getLastSeen()))
                .toList();
    }
}
