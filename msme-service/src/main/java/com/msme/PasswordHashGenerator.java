package com.msme;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {

    public static void main(String[] args) {

        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder(12);

        String rawPassword1 = "hash_riya_001";

        String hash = encoder.encode(rawPassword1);

        System.out.println(hash);
        System.out.println(encoder.encode("hash_riya_001"));

        System.out.println(
                encoder.matches(rawPassword1, hash)
        );
    }
}