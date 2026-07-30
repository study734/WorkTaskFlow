package com.teamproject.resource.storage;

public interface FileStorage {
    void put(String storageKey, byte[] content, String contentType);
    StoredFile get(String storageKey);
    void delete(String storageKey);
    record StoredFile(byte[] content, String contentType) {}
}
