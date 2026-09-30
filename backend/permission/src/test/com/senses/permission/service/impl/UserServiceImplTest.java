package com.senses.permission.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.senses.permission.entity.User;
import com.senses.permission.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by jianfengchen on 2019/8/21.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class UserServiceImplTest {

    @Autowired
    private UserService userService;

    @Test
    public void addUser() throws Exception {
        JSONObject jsonObject = new JSONObject();
        String[] a = {"123","456"};
        jsonObject.put("a",a);

        String as = jsonObject.toJSONString();
        JSONObject jsonb= JSON.parseObject(as);
        JSONArray b = jsonb.getJSONArray("a");
        for(int i = 0;i<b.size();i++){
            Object o = b.get(i);
            System.out.println(o.toString());
        }
    }

    @Test
    public void getUserListByPage() throws Exception {
        Set<Long> s = new HashSet<>();
        s.add(10L);
        s.add(1L);
        s.stream().sorted();
        System.out.println(s);

    }

//    public static void main(String[] args) {
//        Set<Long> s = new HashSet<>();
//        s.add(10L);
//        s.add(1L);
//        s.stream().sorted();
//        System.out.println(s);
//    }

}