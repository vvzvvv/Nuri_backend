package com.nuri.nuribackend.service;

import com.nuri.nuribackend.domain.Chat;
import com.nuri.nuribackend.domain.ChatMessage;
import com.nuri.nuribackend.dto.ChatMessageDto;
import com.nuri.nuribackend.exception.AuthorizationException;
import com.nuri.nuribackend.exception.NotFoundException;
import com.nuri.nuribackend.repository.ChatMessageRepository;
import com.nuri.nuribackend.repository.ChatRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRepository chatRepository;

    public ChatMessageService(ChatMessageRepository chatMessageRepository, ChatRepository chatRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatRepository = chatRepository;
    }

    public List<ChatMessageDto> getMessagesByChatId(Integer chatId) {
        List<ChatMessage> messages = chatMessageRepository.findAllByChatId(chatId);
        return messages.stream()
                .map(ChatMessageDto::fromEntity)
                .collect(Collectors.toList());
    }

    public String getSummaryByChatId(Integer chatId) {
        Chat messages = chatRepository.findByChatId(chatId);
        return messages.getSummary();
    }

    public String getMsgTextByMsgId(String msgId, Long userId) {
        ChatMessage chatMessage = chatMessageRepository.findById(msgId)
                .orElseThrow(() -> new NotFoundException("메시지를 찾을 수 없습니다. msgId = " + msgId));

        Chat chat = chatRepository.findByChatId(chatMessage.getChatId());
        if (chat == null || !chat.getUser().getId().equals(userId)) {
            throw new AuthorizationException("해당 메시지에 접근할 권한이 없습니다.");
        }
        return chatMessage.getMsgText();
    }
}
