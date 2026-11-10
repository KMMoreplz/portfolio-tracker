package com.mikhail.portfolio.error;

/** Код привязки Telegram-бота не найден или уже истёк. */
public class InvalidLinkCodeException extends RuntimeException {

    public InvalidLinkCodeException() {
        super("Код привязки недействителен или истёк");
    }
}
