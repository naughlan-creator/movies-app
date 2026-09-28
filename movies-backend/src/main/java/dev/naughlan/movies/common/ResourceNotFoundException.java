package dev.naughlan.movies.common;

public class ResourceNotFoundException extends RuntimeException {
    private final String title;

    public ResourceNotFoundException(String title, String detail) {
        super(detail);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
