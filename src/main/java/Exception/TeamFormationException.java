package Exception;

public class TeamFormationException extends Exception{
    public TeamFormationException(String message){
        super(message);
    }

    public TeamFormationException(String message, Throwable cause){
        super(message, cause);
    }
}
