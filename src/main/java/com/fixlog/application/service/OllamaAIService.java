package com.fixlog.application.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class OllamaAIService extends AbstractAIService {

    public OllamaAIService(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        super(ChatClient.builder(chatModel).build());
    }
}
