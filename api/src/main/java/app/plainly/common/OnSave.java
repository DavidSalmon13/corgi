package app.plainly.common;

/**
 * Validation group for rules only a save needs, such as the reason the customer will read. A live preview binds its
 * request with plain {@code @Valid} and skips them; the save binds with {@code @Validated({Default.class, OnSave.class})}.
 */
public interface OnSave {}
