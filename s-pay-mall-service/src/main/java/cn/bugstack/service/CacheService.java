package cn.bugstack.service;

import java.util.concurrent.TimeUnit;

public interface CacheService {

    String get(String key);

    void put(String key, String value);

    void put(String key, String value, long timeout, TimeUnit unit);

}