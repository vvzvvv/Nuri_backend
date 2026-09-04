package com.nuri.nuribackend.controller;

import com.nuri.nuribackend.dto.Chat.ChatDto;
import com.nuri.nuribackend.dto.User.UserDto;
import com.nuri.nuribackend.service.ChatListService;
import com.nuri.nuribackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/chatList")
@RequiredArgsConstructor
public class ChatListController {

    private final ChatListService chatListService;
    private final UserService userService;
    @GetMapping
    public ResponseEntity<List<ChatDto>> getAllChat() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("No authentication information.");
        }

        UserDto userDto = userService.getUserByEmail(authentication.getName());
        Long userId = userDto.getId();
        List<ChatDto> chatList;
        chatList = chatListService.getAllChatByUserId(userId);
        return ResponseEntity.ok(chatList);
    }
}
