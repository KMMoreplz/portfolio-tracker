package com.mikhail.portfolio.error;

/** Неверная пара email/пароль при входе. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Неверный email или пароль");
    }
}
