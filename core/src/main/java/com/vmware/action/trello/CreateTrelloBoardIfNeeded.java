package com.vmware.action.trello;

import com.vmware.config.ActionDescription;
import com.vmware.config.WorkflowConfig;
import com.vmware.trello.domain.Board;
import com.vmware.trello.domain.Member;
import com.vmware.utils.input.InputUtils;
import com.vmware.utils.StringUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

@ActionDescription("Creates a trello board if the project name doesn't match an open trello board.")
public class CreateTrelloBoardIfNeeded extends AbstractTrelloAction {


    public CreateTrelloBoardIfNeeded(WorkflowConfig config) {
        super(config);
    }

    @Override
    public void process() throws IOException, IllegalAccessException, URISyntaxException, ParseException {
        String boardName = projectIssues.projectName;

        if (StringUtils.isBlank(boardName)) {
            boardName = InputUtils.readValueUntilNotBlank("Enter trello board name");
        }

        Board[] openBoards = trello.getOpenBoardsForUser();
        Member ownUser = trello.getTrelloMember("me");
        Board matchingBoard = getBoardByName(openBoards, ownUser, boardName);
        if (matchingBoard == null) {
            log.info("No matching trello board found, using name {} for new board", boardName);
            createTrelloBoard(boardName);
            return;
        }

        log.info("Found matching trello board {}", boardName);

        if (matchingBoard.hasOwner(ownUser.id)) {
            selectedBoard = matchingBoard;
            return;
        }

        Member boardOwner = trello.getTrelloMember(matchingBoard.getFirstOwner().idMember);
        log.warn("Board is not owned by you, it's owned by {}", boardOwner.fullName);
        String useOtherBoard = config.ownBoardsOnly ? "n"
                : InputUtils.readValue("Sync jira issues with this existing board? [y/n]");
        if ("y".equalsIgnoreCase(useOtherBoard)) {
            selectedBoard = matchingBoard;
        } else {
            log.info("Creating new board instead");
            createTrelloBoard(boardName);
        }
    }

    private Board getBoardByName(Board[] boards, Member trelloMember, String nameToCheck) throws IOException, URISyntaxException {
        Board firstNonOwnedBoard = null;
        for (Board board : boards) {
            if (board.name.equals(nameToCheck)) {
                if (board.hasOwner(trelloMember.id)) {
                    return board;
                }
                if (firstNonOwnedBoard == null) {
                    firstNonOwnedBoard = board;
                }
            }
        }
        return firstNonOwnedBoard;
    }
}
