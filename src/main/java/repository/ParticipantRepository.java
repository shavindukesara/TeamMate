package repository;

import model.Participant;
import exception.InvalidDataException;
import java.io.IOException;
import java.util.List;

public interface ParticipantRepository {
    List<Participant> load(String filePath) throws IOException, InvalidDataException;
    boolean append(Participant p, String filePath) throws IOException;
}
