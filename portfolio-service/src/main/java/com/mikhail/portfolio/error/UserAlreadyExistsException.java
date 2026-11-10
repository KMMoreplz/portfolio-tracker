package com.mikhail.portfolio.error;

/** Регистрация на уже занятый email. */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String email) {
        super("Пользователь с email=" + email + " уже зарегистрирован");
    }
}
