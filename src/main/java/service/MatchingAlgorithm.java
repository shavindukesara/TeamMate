package service;

import model.*;
import exception.TeamFormationException;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class MatchingAlgorithm implements TeamFormationStrategy {
    private static final Logger LOGGER = Logger.getLogger(MatchingAlgorithm.class.getName());
    private static final int MAX_SAME_GAME = 2;
    private static final int MIN_ROLES = 3;
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final long DETERMINISTIC_SEED = 42L;

    @Override
    public List<Team> formTeams(List<Participant> participants, int teamSize, boolean randomMode)
            throws TeamFormationException {
        return matchParticipants(participants, teamSize, randomMode);
    }

    public static List<Team> matchParticipants(List<Participant> participants, int teamSize)
            throws TeamFormationException {
        return matchParticipants(participants, teamSize, true);
    }

    public static List<Team> matchParticipants(List<Participant> participants, int teamSize, boolean randomMode)
            throws TeamFormationException {

        if (participants == null || participants.isEmpty()) throw new TeamFormationException("No participants provided");
        if (teamSize < 1) throw new TeamFormationException("Team size must be >= 1");

        int numTeams = participants.size() / teamSize;
        if (numTeams == 0) throw new TeamFormationException("Team size too large for participant count");

        Random random = randomMode ? new Random() : new Random(DETERMINISTIC_SEED);
        LOGGER.info("Team formation randomness mode: " + (randomMode ? "RANDOM" : "DETERMINISTIC"));

        double globalAvgSkill = participants.stream().mapToInt(Participant::getSkillLevel).average().orElse(5.5);

        Map<PersonalityType, Queue<Participant>> pools = groupByPersonalityQueues(participants, random);

        Queue<Participant> leadersQ = pools.getOrDefault(PersonalityType.LEADER, new ConcurrentLinkedQueue<>());
        Queue<Participant> thinkersQ = pools.getOrDefault(PersonalityType.THINKER, new ConcurrentLinkedQueue<>());
        Queue<Participant> balancedQ = pools.getOrDefault(PersonalityType.BALANCED, new ConcurrentLinkedQueue<>());

        ConcurrentLinkedQueue<Participant> globalLeftovers = new ConcurrentLinkedQueue<>();

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<Callable<Team>> tasks = new ArrayList<>();
        for (int i = 0; i < numTeams; i++) {
            final int teamIndex = i;
            tasks.add(() -> {
                Team team = new Team("T" + (teamIndex + 1), "Team " + (teamIndex + 1), teamSize);
                List<Participant> reserved = new ArrayList<>();
                boolean success = tryBuildValidTeam(team, teamSize, leadersQ, thinkersQ, balancedQ, reserved, random, globalAvgSkill);
                if (!success) {
                    for (Participant p : reserved) globalLeftovers.add(p);
                    return null;
                }
                return team;
            });
        }

        List<Team> teams = new ArrayList<>();
        try {
            List<Future<Team>> futures = executor.invokeAll(tasks);
            for (Future<Team> f : futures) {
                try {
                    Team t = f.get();
                    if (t != null) teams.add(t);
                } catch (ExecutionException ee) {
                    LOGGER.warning("Team creation task failed: " + ee.getMessage());
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new TeamFormationException("Team formation interrupted");
        } finally {
            executor.shutdown();
        }

        List<Participant> leftovers = new ArrayList<>();
        drainQueueToList(leadersQ, leftovers);
        drainQueueToList(thinkersQ, leftovers);
        drainQueueToList(balancedQ, leftovers);
        drainQueueToList(globalLeftovers, leftovers);

        TeamRebalancer.rebalance(teams);

        List<Team> violating = new ArrayList<>();
        for (Team t : teams) {
            if (teamViolates(t)) violating.add(t);
        }

        if (!violating.isEmpty()) {
            for (Team vt : violating) {
                for (Participant p : vt.getMembers()) leftovers.add(p);
            }
            teams.removeAll(violating);
            LOGGER.info("Removed " + violating.size() + " unstable team(s) and moved their members to leftovers");
        }

        if (!leftovers.isEmpty()) {
            System.out.println("\n" + "=".repeat(55));
            System.out.println("\n            There are " + leftovers.size() + " remaining participants \n            after forming valid teams.");
            System.out.println("            What would you like to do with them?");
            System.out.println("\n            1 - Create additional teams from remaining participants (may be unbalanced) - WARNING");
            System.out.println("            2 - Do NOT create additional teams (remaining participants will be left unteamed)");
            System.out.println("\n" + "=".repeat(55));
            System.out.print("            Your Choice: ");
            String choice = SCANNER.nextLine().trim();
            if ("1".equals(choice)) {
                List<Team> extra = createExtraTeamsFromLeftovers(leftovers, teamSize, teams.size(), random);
                teams.addAll(extra);
                if (!extra.isEmpty()) LOGGER.warning("Created " + extra.size() + " extra team(s) from leftovers (may be unbalanced)");
                leftovers.clear();
            } else {
                LOGGER.info("User chose not to create extra teams for leftover participants");
            }
        }

        writeLeftoversCsv(leftovers);

        validateTeams(teams);

        return teams;
    }

    private static boolean tryBuildValidTeam(Team team,
                                             int teamSize,
                                             Queue<Participant> leadersQ,
                                             Queue<Participant> thinkersQ,
                                             Queue<Participant> balancedQ,
                                             List<Participant> reserved,
                                             Random random,
                                             double globalAvgSkill) {

        if (teamSize == 1) {
            Participant leader = pollCandidateFromQueue(leadersQ, team, globalAvgSkill);
            if (leader != null) { team.addMember(leader); reserved.add(leader); return true; }
            Participant thinker = pollCandidateFromQueue(thinkersQ, team, globalAvgSkill);
            if (thinker != null) { team.addMember(thinker); reserved.add(thinker); return true; }
            Participant bal = pollCandidateFromQueue(balancedQ, team, globalAvgSkill);
            if (bal != null) { team.addMember(bal); reserved.add(bal); return true; }
            return false;
        }

        if (teamSize == 2) {
            Participant leader = pollCandidateFromQueue(leadersQ, team, globalAvgSkill);
            if (leader != null) {
                team.addMember(leader); reserved.add(leader);
                Participant thinker = pollCandidateFromQueue(thinkersQ, team, globalAvgSkill);
                if (thinker != null) { team.addMember(thinker); reserved.add(thinker); return true; }
                Participant bal = pollCandidateFromQueue(balancedQ, team, globalAvgSkill);
                if (bal != null) { team.addMember(bal); reserved.add(bal); return true; }
                Participant thinkerAny = pollAnyFromQueue(thinkersQ);
                if (thinkerAny != null) { team.addMember(thinkerAny); reserved.add(thinkerAny); return true; }
                return false;
            } else {
                Participant thinkerOnly = pollCandidateFromQueue(thinkersQ, team, globalAvgSkill);
                if (thinkerOnly != null) {
                    team.addMember(thinkerOnly); reserved.add(thinkerOnly);
                    Participant bal = pollCandidateFromQueue(balancedQ, team, globalAvgSkill);
                    if (bal != null) { team.addMember(bal); reserved.add(bal); return true; }
                    Participant balAny = pollAnyFromQueue(balancedQ);
                    if (balAny != null) { team.addMember(balAny); reserved.add(balAny); return true; }
                }
                return false;
            }
        }

        if (teamSize == 3) {
            Participant leader = pollCandidateFromQueue(leadersQ, team, globalAvgSkill);
            if (leader == null) return false;
            team.addMember(leader); reserved.add(leader);

            int assignedThinkers = 0;
            for (int i = 0; i < 2 && !team.isFull(); i++) {
                Participant t = pollCandidateFromQueue(thinkersQ, team, globalAvgSkill);
                if (t != null) { team.addMember(t); reserved.add(t); assignedThinkers++; }
            }

            if (assignedThinkers == 2) {
            } else if (assignedThinkers == 1) {
                Participant b = pollCandidateFromQueue(balancedQ, team, globalAvgSkill);
                if (b != null) { team.addMember(b); reserved.add(b); }
                else {
                    Participant any = pollAnyFromQueues(leadersQ, thinkersQ, balancedQ);
                    if (any != null) { team.addMember(any); reserved.add(any); }
                }
            } else {
                Participant b1 = pollCandidateFromQueue(balancedQ, team, globalAvgSkill);
                Participant b2 = pollCandidateFromQueue(balancedQ, team, globalAvgSkill);
                if (b1 != null) { team.addMember(b1); reserved.add(b1); }
                if (b2 != null) { team.addMember(b2); reserved.add(b2); }
                if (team.getCurrentSize() < 3) {
                    Participant any = pollAnyFromQueues(leadersQ, thinkersQ, balancedQ);
                    if (any != null) { team.addMember(any); reserved.add(any); }
                }
            }

            if (!satisfiesPersonalityRule(team)) {
                for (Participant rp : reserved) try { team.removeMember(rp); } catch (Exception ignored) {}
                return false;
            }

            if (team.getUniqueRoleCount() < MIN_ROLES) {
                boolean improved = tryImproveRoleDiversity(team, leadersQ, thinkersQ, balancedQ, reserved, random);
                if (!improved && team.getUniqueRoleCount() < MIN_ROLES) {
                    for (Participant rp : reserved) try { team.removeMember(rp); } catch (Exception ignored) {}
                    return false;
                }
            }

            return true;
        }

        Participant leader = pollCandidateFromQueue(leadersQ, team, globalAvgSkill);
        if (leader == null) return false;
        team.addMember(leader); reserved.add(leader);

        int targetThinkers;
        synchronized (thinkersQ) {
            if (thinkersQ.size() >= 2) {
                targetThinkers = Math.min(2, teamSize - 1);
            } else {
                targetThinkers = 1;
            }
        }

        int assignedThinkers = 0;
        while (assignedThinkers < targetThinkers && !team.isFull()) {
            Participant t = pollCandidateFromQueue(thinkersQ, team, globalAvgSkill);
            if (t == null) break;
            team.addMember(t); reserved.add(t); assignedThinkers++;
        }

        if (assignedThinkers < targetThinkers) {
            while (assignedThinkers < targetThinkers && !team.isFull()) {
                Participant t = pollAnyFromQueue(thinkersQ);
                if (t == null) break;
                if (canAddToTeam(team, t)) {
                    team.addMember(t); reserved.add(t); assignedThinkers++;
                } else {
                    reserved.add(t);
                }
            }
        }

        while (!team.isFull()) {
            Participant b = pollCandidateFromQueue(balancedQ, team, globalAvgSkill);
            if (b == null) break;
            team.addMember(b); reserved.add(b);
        }

        while (!team.isFull()) {
            Participant any = pollAnyFromQueues(leadersQ, thinkersQ, balancedQ);
            if (any == null) break;
            team.addMember(any); reserved.add(any);
        }

        if (!satisfiesPersonalityRule(team)) {
            for (Participant rp : reserved) try { team.removeMember(rp); } catch (Exception ignored) {}
            return false;
        }

        if (team.getUniqueRoleCount() < MIN_ROLES) {
            boolean improved = tryImproveRoleDiversity(team, leadersQ, thinkersQ, balancedQ, reserved, random);
            if (!improved && team.getUniqueRoleCount() < MIN_ROLES) {
                for (Participant rp : reserved) try { team.removeMember(rp); } catch (Exception ignored) {}
                return false;
            }
        }

        return true;
    }

    private static boolean tryImproveRoleDiversity(Team team,
                                                   Queue<Participant> leadersQ,
                                                   Queue<Participant> thinkersQ,
                                                   Queue<Participant> balancedQ,
                                                   List<Participant> reserved,
                                                   Random random) {
        Set<Role> present = new HashSet<>();
        for (Participant m : team.getMembers()) present.add(m.getPreferredRole());
        List<Role> missing = new ArrayList<>();
        for (Role r : Role.values()) if (!present.contains(r)) missing.add(r);
        if (missing.isEmpty()) return true;
        List<Queue<Participant>> pools = Arrays.asList(balancedQ, thinkersQ, leadersQ);
        for (Queue<Participant> pool : pools) {
            synchronized (pool) {
                Iterator<Participant> it = pool.iterator();
                while (it.hasNext()) {
                    Participant candidate = it.next();
                    if (missing.contains(candidate.getPreferredRole()) && canAddToTeam(team, candidate)) {
                        Participant toRemove = team.getMembers().stream()
                                .filter(m -> !missing.contains(m.getPreferredRole()))
                                .findFirst().orElse(null);
                        if (toRemove == null) toRemove = team.getMembers().get(0);
                        boolean removed = team.removeMember(toRemove);
                        if (removed) {
                            boolean removedFromPool = pool.remove(candidate);
                            if (removedFromPool) {
                                team.addMember(candidate);
                                reserved.add(candidate);
                                reserved.add(toRemove);
                                return true;
                            } else {
                                team.addMember(toRemove);
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static Participant pollAnyFromQueues(Queue<Participant> a, Queue<Participant> b, Queue<Participant> c) {
        Participant p = pollAnyFromQueue(a);
        if (p != null) return p;
        p = pollAnyFromQueue(b);
        if (p != null) return p;
        return pollAnyFromQueue(c);
    }

    private static Participant pollAnyFromQueue(Queue<Participant> q) {
        return q == null ? null : q.poll();
    }

    private static Participant pollCandidateFromQueue(Queue<Participant> q, Team team, double targetSkill) {
        if (q == null || q.isEmpty()) return null;
        synchronized (q) {
            Participant best = null;
            double bestDiff = Double.MAX_VALUE;
            for (Participant p : q) {
                if (!canAddToTeam(team, p)) continue;
                double diff = Math.abs(p.getSkillLevel() - targetSkill);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = p;
                }
            }
            if (best != null) {
                q.remove(best);
                return best;
            }
            return q.poll();
        }
    }

    private static Map<PersonalityType, Queue<Participant>> groupByPersonalityQueues(List<Participant> participants, Random random) {
        Map<PersonalityType, List<Participant>> groups = new EnumMap<>(PersonalityType.class);
        for (PersonalityType type : PersonalityType.values()) groups.put(type, new ArrayList<>());
        for (Participant p : participants) groups.get(p.getPersonalityType()).add(p);
        for (List<Participant> list : groups.values()) Collections.shuffle(list, random);
        Map<PersonalityType, Queue<Participant>> queues = new EnumMap<>(PersonalityType.class);
        for (Map.Entry<PersonalityType, List<Participant>> e : groups.entrySet()) {
            queues.put(e.getKey(), new ConcurrentLinkedQueue<>(e.getValue()));
        }
        return queues;
    }

    private static List<Team> createExtraTeamsFromLeftovers(List<Participant> leftovers, int teamSize, int existingCount, Random random) {
        List<Team> extras = new ArrayList<>();
        Collections.shuffle(leftovers, random);
        int index = 0;
        int teamCounter = existingCount;
        while (index < leftovers.size()) {
            int remaining = leftovers.size() - index;
            int currentSize = Math.min(teamSize, remaining);
            Team t = new Team("XT" + (++teamCounter), "Extra Team " + teamCounter, currentSize);
            for (int i = 0; i < currentSize; i++) {
                Participant p = leftovers.get(index++);
                t.addMember(p);
            }
            extras.add(t);
            if (currentSize < teamSize) LOGGER.warning(t.getTeamId() + " created with size " + currentSize + " (smaller than desired team size)");
        }
        return extras;
    }

    private static void drainQueueToList(Queue<Participant> q, List<Participant> out) {
        Participant p;
        while ((p = q.poll()) != null) out.add(p);
    }

    private static void writeLeftoversCsv(List<Participant> leftovers) {
        try {
            Path dir = Paths.get("data");
            Files.createDirectories(dir);
            Path file = dir.resolve("leftovers.csv");
            try (PrintWriter pw = new PrintWriter(new FileWriter(file.toFile(), false))) {
                pw.println("ID,Name,Email,PreferredGame,SkillLevel,PreferredRole,PersonalityScore,PersonalityType");
                for (Participant p : leftovers) {
                    String id = safe(p.getId());
                    String name = safe(p.getName());
                    String email = safe(p.getEmail());
                    String game = safe(p.getPreferredGame());
                    String skill = String.valueOf(p.getSkillLevel());
                    String role = p.getPreferredRole() != null ? safe(p.getPreferredRole().name()) : "";
                    String score = "";
                    try { score = String.valueOf(p.getPersonalityScore()); } catch (Exception e) { score = ""; }
                    String ptype = p.getPersonalityType() != null ? safe(p.getPersonalityType().name()) : "";
                    pw.printf("%s,%s,%s,%s,%s,%s,%s,%s%n",
                            escapeCsv(id), escapeCsv(name), escapeCsv(email), escapeCsv(game),
                            skill, escapeCsv(role), score, escapeCsv(ptype));
                }
            }
            LOGGER.info("Leftovers written to: " + new java.io.File("data/leftovers.csv").getAbsolutePath());
        } catch (Exception e) {
            LOGGER.warning("Failed to write leftovers.csv: " + e.getMessage());
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        boolean need = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String esc = s.replace("\"", "\"\"");
        return need ? "\"" + esc + "\"" : esc;
    }

    private static boolean canAddToTeam(Team team, Participant participant) {
        if (team.countByGame(participant.getPreferredGame()) >= MAX_SAME_GAME) return false;
        return true;
    }

    private static void validateTeams(List<Team> teams) throws TeamFormationException {
        for (Team team : teams) {
            if (team.getUniqueRoleCount() < MIN_ROLES) LOGGER.warning(team.getTeamId() + " has fewer than " + MIN_ROLES + " roles");
        }
        double globalAverage = teams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
        for (Team team : teams) {
            double deviation = Math.abs(team.calculateAverageSkill() - globalAverage);
            if (globalAverage > 0 && deviation > globalAverage * 0.20) LOGGER.warning(team.getTeamId() + " has skill imbalance");
            if (!satisfiesPersonalityRule(team)) LOGGER.warning(team.getTeamId() + " violates personality rule (exactly 1 leader; 1-2 thinkers)");
        }
    }

    private static boolean teamViolates(Team t) {
        if (t.getUniqueRoleCount() < MIN_ROLES) return true;
        if (!satisfiesPersonalityRule(t)) return true;
        return false;
    }

    private static boolean satisfiesPersonalityRule(Team t) {
        int leaders = countByPersonality(t, PersonalityType.LEADER);
        int thinkers = countByPersonality(t, PersonalityType.THINKER);
        if (t.getMaxSize() == 1) {
            return leaders == 1 || thinkers == 1 || countByPersonality(t, PersonalityType.BALANCED) == 1;
        }
        if (t.getMaxSize() == 2) {
            return (leaders == 1 && (thinkers >= 1 || countByPersonality(t, PersonalityType.BALANCED) >= 1))
                    || (leaders == 0 && thinkers >= 1 && countByPersonality(t, PersonalityType.BALANCED) >= 1);
        }
        if (t.getMaxSize() == 3) {
            return leaders == 1 && (thinkers >= 1 || countByPersonality(t, PersonalityType.BALANCED) >= 2);
        }
        return leaders == 1 && thinkers >= 1 && thinkers <= 2;
    }

    private static int countByPersonality(Team t, PersonalityType type) {
        return (int) t.getMembers().stream().filter(m -> m.getPersonalityType() == type).count();
    }
}
