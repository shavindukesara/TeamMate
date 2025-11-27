package service;

import model.Participant;
import model.Team;
import exception.TeamFormationException;

import java.util.List;

public interface TeamFormationStrategy {
    List<Team> formTeams(List<Participant> participants, int teamSize, boolean randomMode)
            throws TeamFormationException;
}

