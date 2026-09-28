package com.example.codingagent.application.port;

import java.util.Map;

public interface ExecutionJournal {

    void record(JournalEvent event, Map<String, ?> data);
}
