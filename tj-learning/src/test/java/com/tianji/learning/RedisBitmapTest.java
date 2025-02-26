package com.tianji.learning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@SpringBootTest(classes = LearningApplication.class)
public class RedisBitmapTest {


    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void test1(){
        Boolean setBit = redisTemplate.opsForValue().setBit("test123", 1, true);
        System.out.println(setBit);
    }

    @Test
    public void test2(){
        List<Long> test123 = redisTemplate.opsForValue().bitField("test123",
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(4)).valueAt(0));
        Long l = test123.get(0);
        System.out.println(l);
    }
}
