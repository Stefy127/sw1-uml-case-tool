package com.sw1.umltool.features.ai.chat.exception;

public class AiConversationNotFoundException extends RuntimeException {
    public AiConversationNotFoundException() { super("Conversación no encontrada."); }
}
