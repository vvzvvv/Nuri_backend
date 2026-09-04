package com.nuri.nuribackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuri.nuribackend.domain.ChatMessage;
import com.nuri.nuribackend.dto.GPT.GPTResponse;
import com.nuri.nuribackend.repository.ChatMessageRepository;
import com.nuri.nuribackend.domain.Feedback.Feedback;
import com.nuri.nuribackend.domain.Feedback.FeedbackContent;
import com.nuri.nuribackend.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GPTService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final ChatMessageRepository chatMessageRepository;
    private final FeedbackRepository feedbackRepository;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.api.model.chat}")
    private String model;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.model.feedback}")
    private String modelFeedback;

    public ChatMessage handleTextGPT(String result) {
        // 사용자 메시지 설정
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", result);
        List<Map<String, String>> messages = List.of(userMessage);

        // GPT 요청 생성
        Map<String, Object> gptRequest = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", 256,
                "temperature", 0.7,
                "top_p", 0.7
        );

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(gptRequest, headers);

        try {

            // GPT API 호출
            GPTResponse gptResponse = restTemplate.postForObject(apiUrl, requestEntity, GPTResponse.class);
            System.out.println("지피티 대답: " + gptResponse);

            // GPT 응답 처리
            if (gptResponse != null && gptResponse.getChoices() != null && !gptResponse.getChoices().isEmpty()) {

                String gptReply = gptResponse.getChoices().get(0).getMessage().getContent();

                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setMsgSound(null); // url
                chatMessage.setMsgText(gptReply);
                chatMessage.setMsgType("gpt");
                chatMessage.setTimeStamp(LocalDateTime.now());

                return chatMessage;

            } else {
                log.error("GPT API에서 응답이 없습니다.");
                return null;
            }
        } catch (Exception e) {
            log.error("GPT API 호출 중 오류가 발생했습니다.", e);
            return null;
        }

    }

    // 피드백 요청 처리
    public Feedback handleFeedbackGPT(String msgId, String msgText, String feedbackType) {
        // 피드백 항목별 system 메시지 설정
        String systemMessage = switch (feedbackType.toLowerCase()) {
            case "grammar" -> "You are an expert in Korean grammar. Analyze the given sentence and identify any grammatical errors or unnatural usage. Provide detailed feedback in English, explaining why these are errors and how they can be improved. If the sentence is appropriate, respond with 'There are no errors. The sentence is correct.'";
            case "vocabulary" -> "You are an expert in Korean vocabulary. Analyze the vocabulary used in the given sentence. Identify any incorrect or unnatural word choices and provide suggestions for improvement. Explain the corrections in English. If the sentence is appropriate, respond with 'There are no errors. The sentence is correct.'";
            case "formalinformal" -> "You are an expert in Korean language formality. Analyze whether the user's last sentence aligns with the appropriate level of formality (formal or informal) based on the context provided in the preceding conversation. If the level of formality is appropriate, respond with 'There are no errors. The sentence is correct' If adjustments are needed, explain the issues and provide suggestions for improvement in English.";
            default -> throw new IllegalArgumentException("Invalid feedback type: " + feedbackType);
        };

        Map<String, String> system = Map.of("role", "system", "content", systemMessage);
        Map<String, String> user = Map.of("role", "user", "content", msgText);
        List<Map<String, String>> messages = List.of(system, user);

        Map<String, Object> gptRequest = Map.of(
                "model", modelFeedback,
                "messages", messages,
                "max_tokens", 256,
                "temperature", 0.7,
                "top_p", 0.7
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(gptRequest, headers);

        try {
            GPTResponse gptResponse = restTemplate.postForObject(apiUrl, requestEntity, GPTResponse.class);

            if (gptResponse != null && gptResponse.getChoices() != null && !gptResponse.getChoices().isEmpty()) {
                String gptReply = gptResponse.getChoices().get(0).getMessage().getContent();

                // 기존 데이터 조회
                Optional<Feedback> existingFeedback = Optional.ofNullable(feedbackRepository.findByMsgId(msgId));
                Feedback feedback;

                if (existingFeedback.isPresent()) {
                    feedback = existingFeedback.get(); // 기존 데이터 가져오기 (중복 저장 방지)
                } else {
                    feedback = new Feedback(); // 새 객체 생성
                    feedback.setMsgId(msgId);     // ID 설정
                }

                // FeedbackContent 객체 생성 (피드백 내용)
                FeedbackContent feedbackContent = new FeedbackContent(gptReply);

                switch (feedbackType.toLowerCase()) {
                    case "grammar" -> feedback.setGrammar(feedbackContent);
                    case "vocabulary" -> feedback.setVocabulary(feedbackContent);
                    case "formalinformal" -> feedback.setFormalInformal(feedbackContent);
                    default -> throw new IllegalArgumentException("Invalid feedback type: " + feedbackType);
                }

                return feedback;

            } else {
                log.error("GPT API에서 응답이 없습니다.");
                return null;
            }
        } catch (Exception e) {
            log.error("GPT API 호출 중 오류가 발생했습니다.", e);
            return null;
        }
    }

}
