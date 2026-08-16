package com.smarthelp.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smarthelp.dto.UserDtos.CreateUserRequest;
import com.smarthelp.dto.UserDtos.UpdateUserRequest;
import com.smarthelp.exception.BadRequestException;
import com.smarthelp.exception.ResourceNotFoundException;
import com.smarthelp.model.User;
import com.smarthelp.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User create(CreateUserRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(existing -> {
            throw new BadRequestException("A user with this email already exists");
        });
        return userRepository.create(request.name(), request.email(), request.role());
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " was not found"));
    }

    public User update(Long id, UpdateUserRequest request) {
        findById(id);
        userRepository.findByEmail(request.email()).ifPresent(existing -> {
            if (!existing.id().equals(id)) {
                throw new BadRequestException("A different user already has this email");
            }
        });
        userRepository.update(id, request.name(), request.email(), request.role());
        return findById(id);
    }

    public boolean existsById(Long id) {
        return userRepository.existsById(id);
    }

    public void delete(Long id) {
        findById(id); // ensures 404 if not found
        userRepository.delete(id);
    }
}
