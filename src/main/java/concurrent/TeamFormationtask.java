package concurrent;

import model.*;
import service.MatchingAlgorithm;
import exception.TeamFormationException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

public class TeamFormationtask implements Callable<List<Team>> {
    private static final Logger LOGGER = Logger.getLogger(TeamFormationtask.class.getName());
    private final List<Participant> participants;
    private final int teamSize;

    public TeamFormationtask(List<Participant> participants, int teamSize) {
        this.participants = participants;
        this.teamSize = teamSize;
    }

    @Override
    public List<Team> call() throws TeamFormationException {
        LOGGER.info("Starting team formation on thread: " + Thread.currentThread().getName());
        long startTime = System.currentTimeMillis();

        List<Team> teams = MatchingAlgorithm.matchParticipants(participants, teamSize);

        long duration = System.currentTimeMillis() - startTime;
        LOGGER.info("Team formation completed in " + duration + "ms");

        return teams;
    }
}
