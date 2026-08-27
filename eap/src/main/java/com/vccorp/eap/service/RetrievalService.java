package com.vccorp.eap.service;

import com.vccorp.eap.dto.RagChatRequest;
import com.vccorp.eap.dto.RagChatResponse;
import com.vccorp.eap.model.User;

public interface RetrievalService {
    RagChatResponse search(RagChatRequest request, User currentUser);
}
