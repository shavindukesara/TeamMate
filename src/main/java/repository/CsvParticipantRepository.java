package repository;

import model.Participant;
import exception.InvalidDataException;
import util.CSVHandler;
import java.io.IOException;
import java.util.List;

public class CsvParticipantRepository implements ParticipantRepository {
    @Override
    public List<Participant> load(String filePath) throws IOException, InvalidDataException {
        return CSVHandler.loadParticipants(filePath);
    }

    @Override
    public boolean append(Participant p, String filePath) throws IOException {
        return CSVHandler.appendParticipantToCsv(p, filePath);
    }
}