package Exception;

public class InsufficientParticipantsException extends Exception{
    private int required;
    private int available;

    public InsufficientParticipantsException(int required, int available) {
        super(String.format("Insufficient participants: required %d, available %d", required, available));
        this.required = required;
        this.available = available;
    }

    public int getRequired() { return required; }
    public int getAvailable() { return available; }
}
