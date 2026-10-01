package io.github.snipperxx.docintel.extraction;

import java.util.List;

/**
 * Proposes facts for one chunk of text. Implementations may be wrong; the pipeline verifies
 * every quote before a fact is stored.
 */
public interface FactExtractor {

    List<CandidateFact> extract(String text);
}
