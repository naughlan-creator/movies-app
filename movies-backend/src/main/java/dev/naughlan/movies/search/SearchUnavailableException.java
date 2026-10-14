package dev.naughlan.movies.search;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class SearchUnavailableException extends ErrorResponseException {

    public SearchUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Search is not available right now"),
                cause);
    }
}