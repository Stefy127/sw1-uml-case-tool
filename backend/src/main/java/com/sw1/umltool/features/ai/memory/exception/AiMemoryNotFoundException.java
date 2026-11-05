package com.sw1.umltool.features.ai.memory.exception;

public class AiMemoryNotFoundException extends RuntimeException {
    public AiMemoryNotFoundException() {
        super("Recuerdo no encontrado.");
    }
}
