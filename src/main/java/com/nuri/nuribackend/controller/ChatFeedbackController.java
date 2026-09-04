package com.nuri.nuribackend.controller;

import com.nuri.nuribackend.domain.Feedback.Feedback;
import com.nuri.nuribackend.domain.Feedback.FeedbackContent;
import com.nuri.nuribackend.dto.User.UserDto;
import com.nuri.nuribackend.service.ChatMessageService;
import com.nuri.nuribackend.service.GPTService;
import com.nuri.nuribackend.service.UserService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chatFeedback")
public class ChatFeedbackController {

    private final ChatMessageService chatMessageService;
    private final GPTService gptService;
    private final MongoTemplate mongoTemplate;
    private final UserService userService;

    public ChatFeedbackController(ChatMessageService chatMessageService,
                                  GPTService gptService,
                                  MongoTemplate mongoTemplate,
                                  UserService userService) {
        this.chatMessageService = chatMessageService;
        this.gptService = gptService;
        this.mongoTemplate = mongoTemplate;
        this.userService = userService;
    }

    @GetMapping("/{feedbackType}/{msgId}")
    public ResponseEntity<FeedbackContent> getFeedback(@PathVariable String feedbackType,
                                                       @PathVariable String msgId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("No authentication information.");
        }
        UserDto userDto = userService.getUserByEmail(authentication.getName());

        String msgText = chatMessageService.getMsgTextByMsgId(msgId, userDto.getId());
        Feedback gptFeedback = gptService.handleFeedbackGPT(msgId, msgText, feedbackType);

        if (gptFeedback == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        FeedbackContent filteredFeedback = switch (feedbackType.toLowerCase()) {
            case "grammar" -> gptFeedback.getGrammar();
            case "vocabulary" -> gptFeedback.getVocabulary();
            case "formalinformal" -> gptFeedback.getFormalInformal();
            default -> throw new IllegalArgumentException("Invalid feedback type: " + feedbackType);
        };

        Query query = Query.query(Criteria.where("msgId").is(msgId));
        Update update = Update.update(feedbackType, filteredFeedback);
        mongoTemplate.upsert(query, update, Feedback.class);

        return ResponseEntity.ok(filteredFeedback);
    }
}
