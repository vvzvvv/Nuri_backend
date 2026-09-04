package com.nuri.nuribackend.controller;

import com.nuri.nuribackend.domain.Feedback.MonthlyFeedback;
import com.nuri.nuribackend.dto.User.UserDto;
import com.nuri.nuribackend.service.RankingService;
import com.nuri.nuribackend.service.UserService;
import com.nuri.nuribackend.service.home.MonthlyFeedbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/home")
@Slf4j
public class HomeController {

    private final MonthlyFeedbackService monthlyFeedbackService;
    private final RankingService rankingService;
    private final UserService userService;

    @Autowired
    public HomeController(MonthlyFeedbackService monthlyFeedbackService, RankingService rankingService, UserService userService) {
        this.monthlyFeedbackService = monthlyFeedbackService;
        this.rankingService = rankingService;
        this.userService = userService;
    }



    @GetMapping("/ranking/allUsers")
    public ResponseEntity<Integer> getAllUserCount() {
        log.info("/ranking/allUsers 컨트롤러 실행");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("No authentication information");
        }

        int allUser = rankingService.getAllUser();
        return ResponseEntity.ok(allUser);
    }

    // 특정 사용자의 순위 반환
    @GetMapping("/ranking")
    public ResponseEntity<Integer> getUserRankingNew() {
        log.info("/ranking 컨트롤러 실행");
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("No authentication information.");
        }

        UserDto userDto = userService.getUserByEmail(authentication.getName());
        Long userId = userDto.getId();
        log.info("ranking 서비스의 userId: " + userId);
        int userRanking = rankingService.getUserRanking(userId);

        return ResponseEntity.ok(userRanking);
    }

    // 특정 사용자의 월별 대화 수 조회 컨트롤러
    @GetMapping("/graph")
    public Map<LocalDate, Integer> getGraph1New() {

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("No authentication information.");
        }

        UserDto userDto = userService.getUserByEmail(authentication.getName());
        Long userId = userDto.getId();

        return rankingService.getGraph(userId);
    }

    // 월별 피드백 조회 컨트롤러
    @GetMapping("/monthlyFeedback/{year}/{month}")
    public MonthlyFeedback getMonthlyFeedbackNew(@PathVariable int year, @PathVariable int month) {

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("No authentication information.");
        }

        UserDto userDto = userService.getUserByEmail(authentication.getName());
        Long userId = userDto.getId();


        return monthlyFeedbackService.getMonthlyFeedback(userId, year, month);
    }

    // 특정 사용자의 순위 반환
    @GetMapping("/ranking/{userId}")
    public ResponseEntity<Integer> getUserRanking(@PathVariable Long userId) {
        log.info("/ranking/{userId} 실행");
        int userRanking = rankingService.getUserRanking(userId);
        return ResponseEntity.ok(userRanking);
    }

    // 특정 사용자의 월별 대화 수 조회 컨트롤러
    @GetMapping("/graph/{userId}")
    public Map<LocalDate, Integer> getGraph(@PathVariable Long userId) {
        return rankingService.getGraph(userId);
    }

    // 월별 피드백 조회 컨트롤러
    @GetMapping("/monthlyFeedback/{userId}/{year}/{month}")
    public MonthlyFeedback getMonthlyFeedback(@PathVariable Long userId, @PathVariable int year, @PathVariable int month) {
        return monthlyFeedbackService.getMonthlyFeedback(userId, year, month);
    }
}
