package com.vccorp.eap.service.search;

import com.vccorp.eap.dto.search.RagChatRequest;
import com.vccorp.eap.dto.search.RagChatResponse;
import com.vccorp.eap.model.User;

public interface RetrievalService {
    RagChatResponse search(RagChatRequest request, User currentUser);
}
