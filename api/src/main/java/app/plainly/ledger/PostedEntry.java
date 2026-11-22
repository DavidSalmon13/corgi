package app.plainly.ledger;

/** A line that is now in the ledger: its row id and the reader-facing id (LE-0001). */
public record PostedEntry(long id, String displayId) {}
