package com.vccorp.eap.service.impl;

import com.vccorp.eap.service.RedisService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Lớp thực thi của RedisService.
 * Tương tác trực tiếp với Redis thông qua StringRedisTemplate để quản lý TTL của token.
 */
@Service
public class RedisServiceImpl implements RedisService {

    /** Template thực thi các câu lệnh Redis kiểu String */
    private final StringRedisTemplate redisTemplate;

    /**
     * Khởi tạo RedisServiceImpl với StringRedisTemplate được Spring quản lý.
     */
    public RedisServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void set(String key, String value, long timeoutMs) {
        redisTemplate.opsForValue().set(key, value, timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }
}
