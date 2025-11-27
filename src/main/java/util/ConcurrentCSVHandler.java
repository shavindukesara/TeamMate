package util;

import model.Participant;
import concurrent.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class ConcurrentCSVHandler {
    private static final Logger LOGGER = Logger.getLogger(ConcurrentCSVHandler.class.getName());
    private static final int CHUNK_SIZE = 25;

    public static List<Participant> loadParticipantsConcurrently(String filePath)
            throws IOException, InterruptedException, ExecutionException {

        LOGGER.info("Loading participants with concurrent processing...");

        List<String[]> allData = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                throw new IOException("Empty CSV file");
            }

            String line;
            while ((line = br.readLine()) != null) {
                allData.add(line.split(","));
            }
        }

        if (allData.isEmpty()) {
            return new ArrayList<>();
        }
        List<List<String[]>> chunks = splitIntoChunks(allData, CHUNK_SIZE);

        List<Callable<List<Participant>>> tasks = new ArrayList<>();
        int startIndex = 1;

        for (List<String[]> chunk : chunks) {
            tasks.add(new DataProcessorTask(chunk, startIndex));
            startIndex += CHUNK_SIZE;
        }

        ThreadPoolManager poolManager = new ThreadPoolManager();
        List<Participant> participants = poolManager.executeTasksAndCollect(tasks);
        poolManager.shutdown();

        LOGGER.info("Loaded " + participants.size() + " participants using " +
                tasks.size() + " concurrent tasks");

        return participants;
    }
    private static <T> List<List<T>> splitIntoChunks(List<T> list, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return chunks;
    }

}
