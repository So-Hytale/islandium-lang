package com.islandium.langeditor.model;

/**
 * Represents a single entry in a .lang file (key=value).
 */
public class LangEntry {

    private final int lineNumber;
    private String key;
    private String value;
    private boolean modified;

    public LangEntry(int lineNumber, String key, String value) {
        this.lineNumber = lineNumber;
        this.key = key;
        this.value = value;
        this.modified = false;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
        this.modified = true;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        this.modified = true;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    /**
     * Returns the formatted line for the .lang file.
     */
    public String toLine() {
        return key + "=" + value;
    }

    /**
     * Extracts a preview of the value without color tags for display.
     */
    public String getPlainValue() {
        return value.replaceAll("<color[^>]*>", "")
                    .replaceAll("</color>", "")
                    .replace("\\n", " ");
    }

    /**
     * Checks if the entry matches a search query (in key or value).
     */
    public boolean matches(String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String lowerQuery = query.toLowerCase();
        return key.toLowerCase().contains(lowerQuery) ||
               value.toLowerCase().contains(lowerQuery);
    }

    @Override
    public String toString() {
        return "LangEntry{" +
                "line=" + lineNumber +
                ", key='" + key + '\'' +
                ", value='" + (value.length() > 50 ? value.substring(0, 50) + "..." : value) + '\'' +
                '}';
    }
}
