package com.islandium.langeditor.service;

import com.islandium.langeditor.model.LangEntry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages loading, searching, and saving .lang files.
 */
public class LangFileManager {

    private static LangFileManager instance;

    private Path currentFilePath;
    private List<LangEntry> entries = new ArrayList<>();
    private List<String> rawLines = new ArrayList<>();

    private LangFileManager() {}

    public static LangFileManager get() {
        if (instance == null) {
            instance = new LangFileManager();
        }
        return instance;
    }

    /**
     * Loads a .lang file from the specified path.
     */
    public boolean loadFile(String path) {
        try {
            currentFilePath = Paths.get(path);
            if (!Files.exists(currentFilePath)) {
                return false;
            }

            entries.clear();
            rawLines.clear();

            List<String> lines = Files.readAllLines(currentFilePath, StandardCharsets.UTF_8);
            rawLines.addAll(lines);

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }

                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex);
                    String value = line.substring(equalsIndex + 1);
                    entries.add(new LangEntry(i, key, value));
                }
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Saves the current entries back to the file.
     */
    public boolean saveFile() {
        if (currentFilePath == null) {
            return false;
        }

        try {
            // Update rawLines with modified entries
            for (LangEntry entry : entries) {
                if (entry.getLineNumber() >= 0 && entry.getLineNumber() < rawLines.size()) {
                    rawLines.set(entry.getLineNumber(), entry.toLine());
                }
            }

            // Add new entries (those with lineNumber = -1)
            for (LangEntry entry : entries) {
                if (entry.getLineNumber() == -1) {
                    rawLines.add(entry.toLine());
                }
            }

            Files.write(currentFilePath, rawLines, StandardCharsets.UTF_8);

            // Mark all entries as not modified
            entries.forEach(e -> e.setModified(false));

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Searches entries by query string.
     */
    public List<LangEntry> search(String query) {
        if (query == null || query.isEmpty()) {
            return new ArrayList<>(entries);
        }

        return entries.stream()
                .filter(e -> e.matches(query))
                .collect(Collectors.toList());
    }

    /**
     * Gets an entry by its key.
     */
    public Optional<LangEntry> getEntry(String key) {
        return entries.stream()
                .filter(e -> e.getKey().equals(key))
                .findFirst();
    }

    /**
     * Gets an entry by index.
     */
    public Optional<LangEntry> getEntryByIndex(int index) {
        if (index >= 0 && index < entries.size()) {
            return Optional.of(entries.get(index));
        }
        return Optional.empty();
    }

    /**
     * Updates an existing entry.
     */
    public boolean updateEntry(String originalKey, String newKey, String newValue) {
        Optional<LangEntry> entryOpt = getEntry(originalKey);
        if (entryOpt.isPresent()) {
            LangEntry entry = entryOpt.get();
            entry.setKey(newKey);
            entry.setValue(newValue);
            return true;
        }
        return false;
    }

    /**
     * Adds a new entry.
     */
    public LangEntry addEntry(String key, String value) {
        // Check if key already exists
        if (getEntry(key).isPresent()) {
            return null;
        }

        LangEntry entry = new LangEntry(-1, key, value);
        entry.setModified(true);
        entries.add(entry);
        return entry;
    }

    /**
     * Deletes an entry by key.
     */
    public boolean deleteEntry(String key) {
        Optional<LangEntry> entryOpt = getEntry(key);
        if (entryOpt.isPresent()) {
            LangEntry entry = entryOpt.get();
            entries.remove(entry);

            // Remove from rawLines if it has a valid line number
            if (entry.getLineNumber() >= 0 && entry.getLineNumber() < rawLines.size()) {
                rawLines.set(entry.getLineNumber(), ""); // Mark as empty instead of removing to preserve line numbers
            }
            return true;
        }
        return false;
    }

    /**
     * Gets all entries.
     */
    public List<LangEntry> getAllEntries() {
        return new ArrayList<>(entries);
    }

    /**
     * Gets the total entry count.
     */
    public int getEntryCount() {
        return entries.size();
    }

    /**
     * Gets the current file path.
     */
    public String getCurrentFilePath() {
        return currentFilePath != null ? currentFilePath.toString() : null;
    }

    /**
     * Gets the current file name.
     */
    public String getCurrentFileName() {
        return currentFilePath != null ? currentFilePath.getFileName().toString() : null;
    }

    /**
     * Checks if there are unsaved changes.
     */
    public boolean hasUnsavedChanges() {
        return entries.stream().anyMatch(LangEntry::isModified);
    }

    /**
     * Lists all .lang files in a directory recursively.
     */
    public List<Path> findLangFiles(String directory) {
        try {
            Path dir = Paths.get(directory);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return Collections.emptyList();
            }

            return Files.walk(dir)
                    .filter(p -> p.toString().endsWith(".lang"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}
