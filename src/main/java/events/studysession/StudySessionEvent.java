package events.studysession;

import events.BotMessageEvent;
import events.ServerConfig;
import events.StudyTimeEvent.StudyTimeRecord;
import model.Bot;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.message.MessageCreateEvent;

import javax.swing.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.TimerTask;


public class StudySessionEvent extends TimerTask implements BotMessageEvent {

    public StudySessionEvent() {
        Timer timer = new Timer(60000, e -> run());
    }

    public void run() {
        StudyTimeRecord.getDueStudySessions().forEach(record -> {
            long timeElapsed = record.finishSession();
            Bot.API.getServersByName("Studium Praetorium").forEach(server ->
                    server.getMemberById(record.getMemberId()).ifPresent(user ->
                            ServerConfig.getRecordsChannelForServer(server).ifPresent(records ->
                                    sendTimeElapsedMessage(records, user.getDisplayName(server), timeElapsed))
                    ));
            record.save();
        });
    }

    @Override
    public void invoke(MessageCreateEvent event, String[] content) {
        getEndOfSession(content).ifPresentOrElse(endEpoch -> event.getServer().ifPresent(server ->
                event.getMessageAuthor().asUser().ifPresent(user -> {
                    if (memberIsInStudyMode(user.getRoles(server), server)) {
                        String memberId = event.getMessageAuthor().getIdAsString();
                        StudyTimeRecord record = StudyTimeRecord.getStudySession(memberId);
                        if (record.inProgress()) {
                            record.finishSession();
                        }
                        record.setEndTime(endEpoch);
                        record.trackSession();
                        ServerConfig.getRecordsChannelForServer(server).ifPresentOrElse(recordsChannel ->
                                        recordsChannel.sendMessage(user.getDisplayName(server) +
                                                " has started studying!"),
                                () -> server.getOwner().ifPresent(owner ->
                                        owner.sendMessage("Please setup a records channel on your server " +
                                                "using `!config study-records <textChannelId>`!")));
                    } else {
                        event.getChannel().sendMessage("You need to be in study mode to start a study session!");
                    }
                })), () -> event.getChannel().sendMessage("The amount of time you specified isn't valid!"));
    }

    private boolean memberIsInStudyMode(List<Role> roles, Server server) {
        if (ServerConfig.getStudyRoleForServer(server).isPresent()) {
            Role studyRole = ServerConfig.getStudyRoleForServer(server).get();
            return roles.contains(studyRole);
        } else {
            return false;
        }
    }


    /**
     * Produce an instance that points to the time when the study session should end.
     *
     * @param content An array of strings that contains words from the user's message.
     * @return returns an Optional that could contain an Instance.
     */
    private Optional<Long> getEndOfSession(String[] content) {
        if (content[0] != null && content[0].matches("\\d*")) {
            long numTemp = Long.parseLong(content[0]);
            if (content[1] != null) {
                Instant now = Instant.now();
                if (content[1].matches("min|m")) {
                    return Optional.of(now.plus(numTemp, ChronoUnit.MINUTES).getEpochSecond());
                } else if (content[1].matches("hr|hours|hour")) {
                    return Optional.of(now.plus(numTemp, ChronoUnit.HOURS).getEpochSecond());
                } else {
                    return Optional.empty();
                }
            }
        } else {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Sends a message that tells for how long the given user has studied.
     *
     * @param timeElapsed amount of time in miliseconds.
     */
    private void sendTimeElapsedMessage(TextChannel records, String name, long timeElapsed) {
        String msg;
        if (timeElapsed / 1000 > 3600)
            msg = name + " has studied for **" + timeElapsed / 60 / 60 + "** hours" + " and " + timeElapsed / 60 % 60
                    + " minutes!";
        else if (timeElapsed > 60)
            msg = name + " has studied for **" + timeElapsed / 60 + "** minutes!";
        else
            msg = name + " has studied for **" + timeElapsed + "** seconds!";
        records.sendMessage(msg);
    }


}
