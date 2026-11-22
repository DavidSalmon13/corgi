package app.plainly.common;

/** Something the caller asked for doesn't exist, or isn't theirs to see. Answered as 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
