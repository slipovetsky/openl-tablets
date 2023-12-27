package org.openl.rules.dt.storage;

class IntMappedStorage extends MappedStorage {

    private final int[] map;

    IntMappedStorage(int[] map, Object[] uniqueValues, IStorage storage, StorageInfo info) {
        super(uniqueValues, storage, info);
        this.map = map;
    }

    @Override
    public final int size() {
        return map.length;
    }

    @Override
    protected int mapIndex(int index) {
        return map[index];
    }

}
