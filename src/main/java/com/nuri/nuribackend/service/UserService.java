package com.nuri.nuribackend.service;

import com.nuri.nuribackend.controller.JwtTokenProvider;
import com.nuri.nuribackend.domain.User;
import com.nuri.nuribackend.dto.User.JwtTokenDto;
import com.nuri.nuribackend.dto.User.SignUpDto;
import com.nuri.nuribackend.dto.User.UserDto;
import com.nuri.nuribackend.exception.CustomException;
import com.nuri.nuribackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public List<UserDto> getAllUsers() {
        try {
            List<User> userList = userRepository.findAll();
            if (userList.isEmpty()) {
                throw new CustomException("NO_USERS_FOUND", "등록된 사용자가 없습니다.");
            }

            List<UserDto> dtoList = new ArrayList<>(userList.size());
            for (User user : userList) {
                UserDto dto = UserDto.toDto(user);
                dtoList.add(dto);
            }

            return List.copyOf(dtoList);
        } catch (Exception ex) {
            log.error("Error retrieving all users: {}", ex.getMessage());
            throw new CustomException("DATABASE_ERROR", "데이터베이스 오류가 발생했습니다.");
        }
    }

    @Transactional
    public UserDto addUser(SignUpDto signUpDto) {
        try {
            if (signUpDto.getEmail() == null || signUpDto.getName() == null) {
                throw new CustomException("INVALID_FORMAT", "Email and Name are required");
            }

            if (userRepository.existsByEmail(signUpDto.getEmail())) {
                throw new CustomException("EXIST_EMAIL", "Email is already used");
            }
            String encodedPassword = passwordEncoder.encode(signUpDto.getPassword());
            System.out.println("PasswordEncoder: " + passwordEncoder);
            System.out.println("Class: " + passwordEncoder.getClass().getName());

            System.out.println(encodedPassword);
            User user = signUpDto.toEntity(encodedPassword);
            User savedUser = userRepository.save(user);
            UserDto response = UserDto.toDto(savedUser);
            return response;
        } catch (CustomException ex){
            log.error("Error adding user: {}", ex.getMessage());
            throw new CustomException(ex.getErrorCode(), ex.getMessage());
        }
    }

    public JwtTokenDto signIn(String username, String password) {
        try {
            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new CustomException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

            // 2. 입력받은 비밀번호와 데이터베이스에 저장된 비밀번호 비교
            System.out.println(!passwordEncoder.matches(password, user.getPassword()));
            if (!passwordEncoder.matches(password, user.getPassword())) {
                throw new CustomException("INVALID_CREDENTIALS", "잘못된 사용자 이름 또는 비밀번호입니다.");
            }

            System.out.println("Username: " + username);
            System.out.println("Password: " + password);


            // 인증 토큰 생성
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);

            // 인증 진행
            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

            // JWT 생성
            return jwtTokenProvider.generateToken(authentication);
        } catch (CustomException ex) {
            log.error("Invalid login attempt for username: {}", username);
            throw new CustomException(ex.getErrorCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Error during user login: {}", ex.getMessage());
            throw new CustomException("AUTHENTICATION_ERROR", "로그인 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public UserDto getUserByUserId(Long id) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new CustomException("USER_NOT_FOUND", "해당 유저를 찾을 수 없습니다."));
            return UserDto.toDto(user);
        } catch (Exception ex) {
            log.error("Error retrieving user by ID {}: {}", id, ex.getMessage());
            throw new CustomException("DATABASE_ERROR", "사용자 조회 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public UserDto getUserByEmail(String email) {
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new CustomException("USER_NOT_FOUND", "해당 유저를 찾을 수 없습니다."));

            return UserDto.toDto(user);
        } catch (Exception ex) {
            throw new CustomException("DATABASE_ERROR", "사용자 조회 중 오류가 발생했습니다.");
        }
    }
}
