package dev.naughlan.movies.common;

// 409 Conflict: the request is valid, but clashes with the current state (e.g. a taken username)
public class ResourceConflictException extends RuntimeException {
    private final String title;

    public ResourceConflictException(String title, String detail) {
        super(detail);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}