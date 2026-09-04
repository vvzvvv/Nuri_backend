package com.nuri.nuribackend.domain.Feedback;

import jakarta.persistence.Id;
import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "feedback")
@Data
public class Feedback {

    @Id
    private String id;

    @Indexed
    private String msgId;   // chatMessage 의 id의 값임(조회시 필요함)

    private FeedbackContent grammar;
    private FeedbackContent vocabulary;
    private FeedbackContent formalInformal;
}