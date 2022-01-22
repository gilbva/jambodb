package com.github.jambodb.storage.btrees;

import com.github.jambodb.storage.blocks.BlockStorage;
import com.github.jambodb.storage.blocks.FileBlockStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class FilePager<K, V> implements Pager<FileBTreePage<K, V>> {
    private static final int BLOCK_SIZE = 8 * 1024;
    private static final int MAX_CACHE = 10;

    private final Path path;
    private final Serializer<K> keySerializer;
    private final Serializer<V> valueSerializer;
    private final Map<Integer, FileBTreePage<K, V>> map;
    private final Queue<Integer> cachePages;
    private final FilePagerHeader header;

    public FilePager(int maxDegree, Path path, Serializer<K> keySerializer, Serializer<V> valueSerializer) throws IOException {
        header = new FilePagerHeader(0, maxDegree, 0);
        this.path = path;
        checkPath();
        this.map = new HashMap<>();
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
        cachePages = new LinkedList<>();
    }

    public FilePager(Path path, Serializer<K> keySerializer, Serializer<V> valueSerializer) throws IOException {
        this.path = path;
        checkPath();
        try (BlockStorage blockStorage = FileBlockStorage.readable(path)) {
            header = FilePagerHeader.read(blockStorage);
            cachePages = new LinkedList<>();
            this.map = new HashMap<>();
            this.keySerializer = keySerializer;
            this.valueSerializer = valueSerializer;
        }
    }

    private void checkPath() throws IOException {
        if (path == null || Files.isDirectory(path)) {
            throw new IOException("Invalid Pager path");
        }
    }

    public int lastPage() {
        return header.lastPage();
    }

    @Override
    public int root() {
        return header.root();
    }

    @Override
    public void root(int id) {
        header.root(id);
    }

    @Override
    public FileBTreePage<K, V> page(int id) throws IOException {
        if (map.get(id) == null && !header.deletedPages().contains(id)) {
            try (BlockStorage blockStorage = FileBlockStorage.readable(path)) {
                FileBTreePage<K, V> page = FileBTreePage.read(id, blockStorage, keySerializer, valueSerializer);
                map.put(id, page);
                addCache(id);
            }
        }
        return map.get(id);
    }

    private void addCache(int id) {
        if (!cachePages.contains(id)) {
            cachePages.add(id);
        }
        while (cachePages.size() > MAX_CACHE) {
            Integer cacheId = cachePages.poll();
            FileBTreePage<K, V> page = map.get(cacheId);
            if (page == null || !page.dirty()) {
                map.remove(cacheId);
            }
        }
    }

    @Override
    public FileBTreePage<K, V> create(boolean leaf) {
        FileBTreePage<K, V> page
            = new FileBTreePage<>(header.lastPage(), leaf, header.maxDegree(), keySerializer, valueSerializer);
        map.put(header.lastPage(), page);
        header.removeDeletedPage(header.lastPage());
        header.incLastPage();
        return page;
    }

    @Override
    public void remove(int id) {
        map.remove(id);
        cachePages.remove(id);
        header.addDeletedPage(id);
        if (header.lastPage() - id == 1) {
            header.incLastPage(-1);
        }
    }

    @Override
    public void fsync() throws IOException {
        Files.deleteIfExists(path);
        try (BlockStorage blockStorage = FileBlockStorage.writeable(BLOCK_SIZE, path)) {
            header.write(blockStorage);
            writePages(blockStorage);
            clearCache();
        }
    }

    private void clearCache() {
        map.clear();
        cachePages.clear();
    }

    private void writePages(BlockStorage blockStorage) throws IOException {
        Collection<FileBTreePage<K, V>> pages = map.values();
        for (FileBTreePage<K, V> page : pages) {
            if (page.dirty() || !cachePages.contains(page.id())) {
                page.fsync(blockStorage);
            }
        }
    }
}
