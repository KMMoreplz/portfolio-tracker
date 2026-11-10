package com.mikhail.telegrambot.error;

/** JWT пользователя не принят portfolio-service (истёк или отозван) — нужна повторная привязка. */
public class TokenExpiredException extends RuntimeException {

    public TokenExpiredException() {
        super("Токен пользователя не принят portfolio-service");
    }
}
