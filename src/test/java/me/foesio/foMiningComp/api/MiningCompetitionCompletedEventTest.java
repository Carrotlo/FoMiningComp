package me.foesio.foMiningComp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MiningCompetitionCompletedEventTest {

    @Test
    void snapshotsPlacements() {
        List<MiningCompetitionCompletedEvent.Placement> source = new ArrayList<>();
        source.add(new MiningCompetitionCompletedEvent.Placement(UUID.randomUUID(), "Miner", 25, 1));

        MiningCompetitionCompletedEvent event =
            new MiningCompetitionCompletedEvent("blocks", "Blocks", source);
        source.clear();

        assertEquals(1, event.getPlacements().size());
        assertEquals(CompletionReason.UNKNOWN, event.getReason());
        assertThrows(UnsupportedOperationException.class, () -> event.getPlacements().clear());
    }

    @Test
    void rejectsInvalidRankAndNormalizesNullName() {
        UUID playerId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
            () -> new MiningCompetitionCompletedEvent.Placement(playerId, "Miner", 25, 0));
        assertTrue(new MiningCompetitionCompletedEvent.Placement(playerId, null, 25, 1).playerName().isEmpty());
    }
}
