package com.example.autentication.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EmailAlreadyExistsError extends ResponseStatusException {
    public EmailAlreadyExistsError(){
        super(HttpStatus.BAD_REQUEST, "Email existente");
    }
}
