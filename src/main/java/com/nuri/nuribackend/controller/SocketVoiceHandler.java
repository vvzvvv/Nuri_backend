package com.nuri.nuribackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuri.nuribackend.domain.Ranking;
import com.nuri.nuribackend.domain.User;
import com.nuri.nuribackend.dto.ChatDto;
import com.nuri.nuribackend.dto.Feedback.RankingDto;
import com.nuri.nuribackend.dto.User.UserDto;
import com.nuri.nuribackend.repository.ChatMessageRepository;
import com.nuri.nuribackend.domain.ChatMessage;
import com.nuri.nuribackend.repository.RankingRepository;
import com.nuri.nuribackend.service.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.TranscriptionJobStatus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SocketVoiceHandler extends AbstractWebSocketHandler {
    private final ObjectMapper mapper;
    private final Set<WebSocketSession> sessions = new HashSet<>();
    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;
    private final S3Service s3Service;
    private final GPTService gptService;  // GPTService
    private final PollyService pollyService;
    private final ChatService chatService;
    private final RankingRepository rankingRepository;
    private final RankingService rankingService;
    private final ChatSummaryService chatSummaryService;

    private int newChatId;
    private String userName;
    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException{
        Map<String, Object> attributes = session.getAttributes();
        userName = (String) attributes.get("user_name");
        System.out.println(userName);
        UserDto userDto = userService.getUserByUserName(userName);
        ChatDto chatDto= new ChatDto();

        chatDto.setUser(userDto.toEntity());
        chatDto.setDate(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));
        chatDto.setUser_msg_cnt(0);
        ChatDto savedChat = chatService.addChat(chatDto);
        newChatId = savedChat.getChatId();

        if (userName != null) {
            System.out.println("Authenticated User: " + userName);
        } else {
            System.out.println("No user information found in session attributes");
            session.close(CloseStatus.NOT_ACCEPTABLE);
        }

        log.info("Voice Connected: {} - Total sessions: {}", session.getId(), sessions.size());


        // 클라이언트로 newChatId 전송
        Map<String, Object> initialMessage = new HashMap<>();
        initialMessage.put("type", "INITIAL");
        initialMessage.put("chatId", newChatId);

        String initialMessageJson = objectMapper.writeValueAsString(initialMessage);
        session.sendMessage(new TextMessage(initialMessageJson));

    }


    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteBuffer payload = message.getPayload();
        log.info("Received binary message from session: {} - Payload size: {}", session.getId(), payload.remaining());

        byte[] audioData = new byte[payload.remaining()];
        payload.get(audioData);

        try {
            processAudioData(session, audioData);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Audio received");
            String jsonResponse = objectMapper.writeValueAsString(response);
            //session.sendMessage(new TextMessage(jsonResponse));
        } catch (Exception e) {
            log.error("Error processing audio data for session: {}", session.getId(), e);
            try {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Error processing audio data");
                String jsonResponse = objectMapper.writeValueAsString(response);
                session.sendMessage(new TextMessage(jsonResponse));
            } catch (IOException ex) {
                log.error("Error sending error message to client for session: {}", session.getId(), ex);
            }
        }
    }

    private void processAudioData(WebSocketSession session, byte[] audioData) {
        log.info("Processing audio data from session: {} - Size: {}", session.getId(), audioData.length);

        String contentType = "audio/mpeg";
        String fileName = "voice/" + "audio-" + UUID.randomUUID().toString() + ".mp3";

        new Thread(() -> {
            try {
                log.info("Uploading audio to S3 - FileName: {}", fileName);
                s3Service.uploadByteArrayToS3("nuri-s3", fileName, audioData, contentType);
                log.info("Successfully uploaded audio to S3 - FileName: {}", fileName);
                transcriptAudioData(session, fileName);
            } catch (Exception e) {
                log.error("Error uploading audio data to S3 for session: {} - FileName: {}", session.getId(), fileName, e);
            }
        }).start();
    }

    protected void transcriptAudioData(WebSocketSession session, String url) throws InterruptedException, IOException {
        TranscribeClient transcribeClient = TranscribeClient.builder()
                .region(Region.AP_NORTHEAST_2) // 리전 설정
                .build();
        TranscribeService transcribeService = new TranscribeService(transcribeClient);
        String bucketName = "nuri-s3";
        String jobName = "my-transcription-job-" + UUID.randomUUID();

        String transcriptionJobName = transcribeService.startTranscriptionJob(bucketName, url, jobName);
        System.out.println("Started Transcription Job: " + transcriptionJobName);

        TranscriptionJobStatus status;
        do {
            status = transcribeService.checkTranscriptionJobStatus(transcriptionJobName);
            System.out.println("Transcription Job Status: " + status);
            Thread.sleep(5000); // 5초 대기
        } while (status == TranscriptionJobStatus.IN_PROGRESS);

        // 작업 완료 시 결과 URL 가져오기
        if (status == TranscriptionJobStatus.COMPLETED) {
            String result = transcribeService.getTranscriptionJobResult(transcriptionJobName);
            System.out.println("Transcription Result URL: " + result);
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setMsgType("user");
            chatMessage.setMsgText(result);
            chatMessage.setMsgSound(url);
            chatMessage.setChatId(newChatId);
            chatMessage.setTimeStamp(LocalDateTime.now().atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime());
            ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
            chatService.addChatCnt(newChatId);

            String msgId = savedMessage.getId(); // 저장된 msgId를 가져옴
            chatMessage.setId(msgId); // 클라이언트에 msgId도 전송 (후에 피드백 요청시 사용)

            String responseJson = mapper.writeValueAsString(chatMessage);
            session.sendMessage(new TextMessage(responseJson));

            System.out.println("Generated Chat ID: " + savedMessage.getChatId().toString());

            // GPT API
            ChatMessage gptMessage = gptService.handleTextGPT(result);
            gptMessage.setChatId(newChatId);
            String gptResponseJson = mapper.writeValueAsString(gptMessage);
            session.sendMessage(new TextMessage(gptResponseJson));
            chatMessageRepository.save(gptMessage);

            pollyService.initializePollyClient();
            String audioFileName = "polly/" + "speech-" + UUID.randomUUID() + ".mp3";
            try {
                byte[] audioData = pollyService.synthesizeSpeechToByteArray(gptMessage.getMsgText());
                s3Service.uploadByteArrayToS3(bucketName, audioFileName, audioData, "audio/mpeg");

                // 클라이언트로 음성 URL 전달
                String audioUrl = s3Service.getFileUrl(bucketName, audioFileName);

                Map<String, String> response = new HashMap<>();
                response.put("audioUrl", audioUrl); // S3 URL 추가
                response.put("message", "Audio has been generated successfully");
                String jsonResponse = objectMapper.writeValueAsString(response);

                session.sendMessage(new TextMessage(jsonResponse));
            } catch (Exception e) {
                log.error("Error generating or sending Polly audio for session: {}", session.getId(), e);
            }

        } else {
            System.out.println("Transcription Job Failed.");
        }

        transcribeClient.close();
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        ChatDto chatDto = chatService.getChatByChatId(newChatId);
        //chatSummaryService.getChatSummary(newChatId);

        LocalDateTime localDateTime = chatDto.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        int year = localDateTime.getYear();
        int month = localDateTime.getMonthValue();

        Map<String, Object> attributes = session.getAttributes();
        userName = (String) attributes.get("user_name");
        System.out.println(userName);
        UserDto userDto = userService.getUserByUserName(userName);

        Optional<Ranking> optionalRanking = rankingRepository.findByUserIdAndYearAndMonth(userDto.getId(), year, month);

        RankingDto rankingDto;
        // 사용자가 year.month 날짜에 대화를 하지 않은 경우
        if (!optionalRanking.isPresent()) {
            rankingDto = new RankingDto();

            rankingDto.setUser(userDto.toEntity());
            rankingDto.setMsgTotalCnt(chatDto.getUser_msg_cnt());

            Date saveToDate = new Date(year-1900, month-1, 1);
            rankingDto.setDate(saveToDate);
        }
        // 사용자가 year.month 날짜에 대화를 한 경우
        else {
            Ranking ranking = optionalRanking.get();
            rankingDto = new RankingDto();
            rankingDto = rankingDto.toDto(ranking);
            rankingDto.setMsgTotalCnt(rankingDto.getMsgTotalCnt() + chatDto.getUser_msg_cnt());
        }

        rankingService.addRanking(rankingDto);
        log.info("Voice Disconnected: {} - CloseStatus: {} - Remaining sessions: {}", session.getId(), status, sessions.size());
    }

    private Integer generateNewChatId() {
        return (int) (UUID.randomUUID().getLeastSignificantBits() & 0x7FFFFFFF);
    }

}
