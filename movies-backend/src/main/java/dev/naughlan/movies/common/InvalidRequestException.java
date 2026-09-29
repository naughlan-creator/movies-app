package dev.naughlan.movies.common;

// 400 for rules that annotations can't express, reported in the same errors-map shape
public class InvalidRequestException extends RuntimeException {
    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}