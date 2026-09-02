package com.nuri.nuribackend.controller;

import com.nuri.nuribackend.domain.Feedback.Feedback;
import com.nuri.nuribackend.domain.Feedback.FeedbackContent;
import com.nuri.nuribackend.repository.FeedbackRepository;
import com.nuri.nuribackend.service.ChatMessageService;
import com.nuri.nuribackend.service.GPTService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chatFeedback")
public class ChatFeedbackController {

    private final ChatMessageService chatMessageService;
    private final GPTService gptService;
    private final FeedbackRepository feedbackRepository;


    public ChatFeedbackController(ChatMessageService chatMessageService,
                                  GPTService gptService,
                                  FeedbackRepository feedbackRepository) {
        this.chatMessageService = chatMessageService;
        this.gptService = gptService;
        this.feedbackRepository = feedbackRepository;
    }

    @GetMapping("/{feedbackType}/{msgId}")
    public ResponseEntity<FeedbackContent> getFeedback(
            @PathVariable String feedbackType,
            @PathVariable String msgId
    ) {
        String msgText = chatMessageService.getMsgTextByMsgId(msgId);
        Feedback gptFeedback = gptService.handleFeedbackGPT(msgId, msgText, feedbackType);
        FeedbackContent filteredFeedback = switch (feedbackType.toLowerCase()) {
            case "grammar" -> gptFeedback.getGrammar();
            case "vocabulary" -> gptFeedback.getVocabulary();
            case "formalinformal" -> gptFeedback.getFormalInformal();
            default -> throw new IllegalArgumentException("Invalid feedback type: " + feedbackType);
        };

        feedbackRepository.save(gptFeedback);

        return ResponseEntity.ok(filteredFeedback);
    }
}
